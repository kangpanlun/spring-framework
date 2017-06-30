/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.function.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerWebExchange;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

import static org.junit.Assert.*;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;

/**
 * @author Arjen Poutsma
 */
public class DefaultServerRequestTests {

	List<HttpMessageReader<?>> messageReaders;


	@Before
	public void createMocks() {
		this.messageReaders = Collections.<HttpMessageReader<?>>singletonList(new DecoderHttpMessageReader<>(StringDecoder.allMimeTypes(true)));
	}


	@Test
	public void method() throws Exception {
		HttpMethod method = HttpMethod.HEAD;
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(method, "http://example.com").build();
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		assertEquals(method, request.method());
	}

	@Test
	public void uri() throws Exception {
		URI uri = URI.create("https://example.com");

		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, uri).build();
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		assertEquals(uri, request.uri());
	}

	@Test
	public void attribute() throws Exception {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com").build();
		MockServerWebExchange exchange = new MockServerWebExchange(mockRequest);
		exchange.getAttributes().put("foo", "bar");

		DefaultServerRequest request = new DefaultServerRequest(exchange, messageReaders);

		assertEquals(Optional.of("bar"), request.attribute("foo"));
	}

	@Test
	public void queryParams() throws Exception {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com?foo=bar").build();
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		assertEquals(Optional.of("bar"), request.queryParam("foo"));
	}

	@Test
	public void emptyQueryParam() throws Exception {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com?foo").build();
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		assertEquals(Optional.of(""), request.queryParam("foo"));
	}

	@Test
	public void pathVariable() throws Exception {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com").build();
		MockServerWebExchange exchange = new MockServerWebExchange(mockRequest);
		Map<String, String> pathVariables = Collections.singletonMap("foo", "bar");
		exchange.getAttributes().put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);

		DefaultServerRequest request = new DefaultServerRequest(exchange, messageReaders);

		assertEquals("bar", request.pathVariable("foo"));
	}


	@Test(expected = IllegalArgumentException.class)
	public void pathVariableNotFound() throws Exception {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com").build();
		MockServerWebExchange exchange = new MockServerWebExchange(mockRequest);
		Map<String, String> pathVariables = Collections.singletonMap("foo", "bar");
		exchange.getAttributes().put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);

		DefaultServerRequest request = new DefaultServerRequest(exchange, messageReaders);

		request.pathVariable("baz");
	}

	@Test
	public void pathVariables() throws Exception {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com").build();
		MockServerWebExchange exchange = new MockServerWebExchange(mockRequest);
		Map<String, String> pathVariables = Collections.singletonMap("foo", "bar");
		exchange.getAttributes().put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);

		DefaultServerRequest request = new DefaultServerRequest(exchange, messageReaders);

		assertEquals(pathVariables, request.pathVariables());
	}

	@Test
	public void header() throws Exception {
		HttpHeaders httpHeaders = new HttpHeaders();
		List<MediaType> accept =
				Collections.singletonList(MediaType.APPLICATION_JSON);
		httpHeaders.setAccept(accept);
		List<Charset> acceptCharset = Collections.singletonList(StandardCharsets.UTF_8);
		httpHeaders.setAcceptCharset(acceptCharset);
		long contentLength = 42L;
		httpHeaders.setContentLength(contentLength);
		MediaType contentType = MediaType.TEXT_PLAIN;
		httpHeaders.setContentType(contentType);
		InetSocketAddress host = InetSocketAddress.createUnresolved("localhost", 80);
		httpHeaders.setHost(host);
		List<HttpRange> range = Collections.singletonList(HttpRange.createByteRange(0, 42));
		httpHeaders.setRange(range);

		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com?foo=bar").
				headers(httpHeaders).build();
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		ServerRequest.Headers headers = request.headers();
		assertEquals(accept, headers.accept());
		assertEquals(acceptCharset, headers.acceptCharset());
		assertEquals(OptionalLong.of(contentLength), headers.contentLength());
		assertEquals(Optional.of(contentType), headers.contentType());
		assertEquals(httpHeaders, headers.asHttpHeaders());
	}

	@Test
	public void body() throws Exception {
		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.TEXT_PLAIN);

		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com?foo=bar").
				headers(httpHeaders).body(body);
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		Mono<String> resultMono = request.body(toMono(String.class));
		assertEquals("foo", resultMono.block());
	}

	@Test
	public void bodyToMono() throws Exception {
		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.TEXT_PLAIN);
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com?foo=bar").
				headers(httpHeaders).body(body);
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		Mono<String> resultMono = request.bodyToMono(String.class);
		assertEquals("foo", resultMono.block());
	}

	@Test
	public void bodyToFlux() throws Exception {
		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.TEXT_PLAIN);
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com?foo=bar").
				headers(httpHeaders).body(body);
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		Flux<String> resultFlux = request.bodyToFlux(String.class);
		assertEquals(Collections.singletonList("foo"), resultFlux.collectList().block());
	}

	@Test
	public void bodyUnacceptable() throws Exception {
		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.TEXT_PLAIN);
		MockServerHttpRequest mockRequest = MockServerHttpRequest.method(HttpMethod.GET, "http://example.com?foo=bar").
				headers(httpHeaders).body(body);
		this.messageReaders = Collections.emptyList();
		DefaultServerRequest request = new DefaultServerRequest(mockRequest.toExchange(), messageReaders);

		Flux<String> resultFlux = request.bodyToFlux(String.class);
		StepVerifier.create(resultFlux)
				.expectError(UnsupportedMediaTypeStatusException.class)
				.verify();
	}
}
