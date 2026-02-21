Vert.x Web Client
Vert.x Web Client is an asynchronous HTTP and HTTP/2 client.

The Web Client makes easy to do HTTP request/response interactions with a web server, and provides advanced features like:

Json body encoding / decoding

request/response pumping

request parameters

unified error handling

form submissions

The Web Client does not deprecate the Vert.x Core HttpClient, indeed it is based on this client and inherits its configuration and great features like pooling, HTTP/2 support, pipelining support, etc…​ The HttpClient should be used when fine grained control over the HTTP requests/responses is necessary.

The Web Client does not provide a WebSocket API, the Vert.x Core HttpClient should be used. It also does not handle cookies at the moment.

Using the Web Client
To use Vert.x Web Client, add the following dependency to the dependencies section of your build descriptor:

Maven (in your pom.xml):

<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-web-client</artifactId>
  <version>5.0.8</version>
</dependency>
Gradle (in your build.gradle file):

dependencies {
compile 'io.vertx:vertx-web-client:5.0.8'
}
Re-cap on Vert.x core HTTP client
Vert.x Web Client uses the API from Vert.x core, so it’s well worth getting familiar with the basic concepts of using HttpClient using Vert.x core, if you’re not already.

Creating a Web Client
You create an WebClient instance with default options as follows

WebClient client = WebClient.create(vertx);
If you want to configure options for the client, you create it as follows

WebClientOptions options = new WebClientOptions()
.setUserAgent("My-App/1.2.3");
options.setKeepAlive(false);
WebClient client = WebClient.create(vertx, options);
Web Client options inherit Http Client options so you can set any one of them.

If your already have an HTTP Client in your application you can also reuse it

WebClient client = WebClient.wrap(httpClient);
In most cases, a Web Client should be created once on application startup and then reused. Otherwise you lose a lot of benefits such as connection pooling and may leak resources if instances are not closed properly.
Making requests
Simple requests with no body
Often, you’ll want to make HTTP requests with no request body. This is usually the case with HTTP GET, OPTIONS and HEAD requests

WebClient client = WebClient.create(vertx);

// Send a GET request
client
.get(8080, "myserver.mycompany.com", "/some-uri")
.send()
.onSuccess(response -> System.out
.println("Received response with status code" + response.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));

// Send a HEAD request
client
.head(8080, "myserver.mycompany.com", "/some-uri")
.send()
.onSuccess(response -> System.out
.println("Received response with status code" + response.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
You can add query parameters to the request URI in a fluent fashion

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.addQueryParam("param", "param_value")
.send()
.onSuccess(response -> System.out
.println("Received response with status code" + response.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
Any request URI parameter will pre-populate the request

HttpRequest<Buffer> request = client
.get(
8080,
"myserver.mycompany.com",
"/some-uri?param1=param1_value&param2=param2_value");

// Add param3
request.addQueryParam("param3", "param3_value");

// Overwrite param2
request.setQueryParam("param2", "another_param2_value");
Setting a request URI discards existing query parameters

HttpRequest<Buffer> request = client
.get(8080, "myserver.mycompany.com", "/some-uri");

// Add param1
request.addQueryParam("param1", "param1_value");

// Overwrite param1 and add param2
request.uri("/some-uri?param1=param1_value&param2=param2_value");
Writing request bodies
When you need to make a request with a body, you use the same API and call then sendXXX methods that expects a body to send.

Use sendBuffer to send a buffer body

client
.post(8080, "myserver.mycompany.com", "/some-uri")
.sendBuffer(buffer)
.onSuccess(res -> {
// OK
});
Sending a single buffer is useful but often you don’t want to load fully the content in memory because it may be too large or you want to handle many concurrent requests and want to use just the minimum for each request. For this purpose the Web Client can send ReadStream<Buffer> (e.g a AsyncFile is a ReadStream<Buffer>`) with the sendStream method

client
.post(8080, "myserver.mycompany.com", "/some-uri")
.sendStream(stream)
.onSuccess(res -> {
// OK
});
The Web Client takes care of setting up the transfer pump for you. Since the length of the stream is not know the request will use chunked transfer encoding .

When you know the size of the stream, you shall specify before using the content-length header

fs.open("content.txt", new OpenOptions())
.onSuccess(fileStream -> {
String fileLen = "1024";

    // Send the file to the server using POST
    client
      .post(8080, "myserver.mycompany.com", "/some-uri")
      .putHeader("content-length", fileLen)
      .sendStream(fileStream)
      .onSuccess(res -> {
        // OK
      });
});
The POST will not be chunked.

Json bodies
Often you’ll want to send Json body requests, to send a JsonObject use the sendJsonObject

client
.post(8080, "myserver.mycompany.com", "/some-uri")
.sendJsonObject(
new JsonObject()
.put("firstName", "Dale")
.put("lastName", "Cooper"))
.onSuccess(res -> {
// OK
});
In Java, Groovy or Kotlin, you can use the sendJson method that maps a POJO (Plain Old Java Object) to a Json object using Json.encode method

client
.post(8080, "myserver.mycompany.com", "/some-uri")
.sendJson(new User("Dale", "Cooper"))
.onSuccess(res -> {
// OK
});
the Json.encode uses the Jackson mapper to encode the object to Json.
Form submissions
You can send http form submissions bodies with the sendForm variant.

MultiMap form = MultiMap.caseInsensitiveMultiMap();
form.set("firstName", "Dale");
form.set("lastName", "Cooper");

// Submit the form as a form URL encoded body
client
.post(8080, "myserver.mycompany.com", "/some-uri")
.sendForm(form)
.onSuccess(res -> {
// OK
});
By default the form is submitted with the application/x-www-form-urlencoded content type header. You can set the content-type header to multipart/form-data instead

MultiMap form = MultiMap.caseInsensitiveMultiMap();
form.set("firstName", "Dale");
form.set("lastName", "Cooper");

// Submit the form as a multipart form body
client
.post(8080, "myserver.mycompany.com", "/some-uri")
.putHeader("content-type", "multipart/form-data")
.sendForm(form)
.onSuccess(res -> {
// OK
});
If you want to upload files and send attributes, you can create a MultipartForm and use sendMultipartForm.

MultipartForm form = MultipartForm.create()
.attribute("imageDescription", "a very nice image")
.binaryFileUpload(
"imageFile",
"image.jpg",
"/path/to/image",
"image/jpeg");

// Submit the form as a multipart form body
client
.post(8080, "myserver.mycompany.com", "/some-uri")
.sendMultipartForm(form)
.onSuccess(res -> {
// OK
});
Writing request headers
You can write headers to a request using the headers multi-map as follows:

HttpRequest<Buffer> request = client
.get(8080, "myserver.mycompany.com", "/some-uri");

MultiMap headers = request.headers();
headers.set("content-type", "application/json");
headers.set("other-header", "foo");
The headers are an instance of MultiMap which provides operations for adding, setting and removing entries. Http headers allow more than one value for a specific key.

You can also write headers using putHeader

HttpRequest<Buffer> request = client
.get(8080, "myserver.mycompany.com", "/some-uri");

request.putHeader("content-type", "application/json");
request.putHeader("other-header", "foo");
Configure the request to add authentication.
Authentication can be performed manually by setting the correct headers, or, using our predefined methods (We strongly suggest having HTTPS enabled, especially for authenticated requests):

In basic HTTP authentication, a request contains a header field of the form Authorization: Basic <credentials>, where credentials is the base64 encoding of id and password joined by a colon.

You can configure the request to add basic access authentication as follows:

HttpRequest<Buffer> request = client
.get(8080, "myserver.mycompany.com", "/some-uri")
.authentication(new UsernamePasswordCredentials("myid", "mypassword"));
In OAuth 2.0, a request contains a header field of the form Authorization: Bearer <bearerToken>, where bearerToken is the bearer token issued by an authorization server to access protected resources.

You can configure the request to add bearer token authentication as follows:

HttpRequest<Buffer> request = client
.get(8080, "myserver.mycompany.com", "/some-uri")
.authentication(new TokenCredentials("myBearerToken"));
Reusing requests
The send method can be called multiple times safely, making it very easy to configure and reuse HttpRequest objects

HttpRequest<Buffer> get = client
.get(8080, "myserver.mycompany.com", "/some-uri");

get
.send()
.onSuccess(res -> {
// OK
});

// Same request again
get
.send()
.onSuccess(res -> {
// OK
});
Beware though that HttpRequest instances are mutable. Therefore you should call the copy method before modifying a cached instance.

HttpRequest<Buffer> get = client
.get(8080, "myserver.mycompany.com", "/some-uri");

get
.send()
.onSuccess(res -> {
// OK
});

// The "get" request instance remains unmodified
get
.copy()
.putHeader("a-header", "with-some-value")
.send()
.onSuccess(res -> {
// OK
});
Timeouts
You can set a connect timeout for a specific http request using connectTimeout.

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.connectTimeout(5000)
.send()
.onSuccess(res -> {
// OK
})
.onFailure(err -> {
// Might be a timeout when cause is java.util.concurrent.TimeoutException
});
If the client cannot obtain a connection to the server within the timeout period an exception will be passed to the response handler.

You can set an idle timeout for a specific http request using idleTimeout.

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.idleTimeout(5000)
.send()
.onSuccess(res -> {
// OK
})
.onFailure(err -> {
// Might be a timeout when cause is java.util.concurrent.TimeoutException
});
If the request does not return any data within the timeout period an exception will be passed to the response handler.

You can set both timeouts using timeout

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.timeout(5000)
.send()
.onSuccess(res -> {
// OK
})
.onFailure(err -> {
// Might be a timeout when cause is java.util.concurrent.TimeoutException
});
Handling http responses
When the Web Client sends a request you always deal with a single async result HttpResponse.

On a success result the callback happens after the response has been received

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
By default, a Vert.x Web Client request ends with an error only if something wrong happens at the network level. In other words, a 404 Not Found response, or a response with the wrong content type, are not considered as failures. Use http response expectations if you want the Web Client to perform sanity checks automatically.

Responses are fully buffered, use BodyCodec.pipe to pipe the response to a write stream
Decoding responses
By default the Web Client provides an http response body as a Buffer and does not apply any decoding.

Custom response body decoding can be achieved using BodyCodec:

Plain String

Json object

Json mapped POJO

WriteStream

A body codec can decode an arbitrary binary data stream into a specific object instance, saving you the decoding step in your response handlers.

Use BodyCodec.jsonObject To decode a Json object:

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.as(BodyCodec.jsonObject())
.send()
.onSuccess(res -> {
JsonObject body = res.body();

    System.out.println(
      "Received response with status code" +
        res.statusCode() +
        " with body " +
        body);
})
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
In Java, Groovy or Kotlin, custom Json mapped POJO can be decoded

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.as(BodyCodec.json(User.class))
.send()
.onSuccess(res -> {
User user = res.body();

    System.out.println(
      "Received response with status code" +
        res.statusCode() +
        " with body " +
        user.getFirstName() +
        " " +
        user.getLastName());
})
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
When large response are expected, use the BodyCodec.pipe. This body codec pumps the response body buffers to a WriteStream and signals the success or the failure of the operation in the async result response

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.as(BodyCodec.pipe(writeStream))
.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
It becomes frequent to see API returning a stream of JSON objects. For example, the Twitter API can provides a feed of tweets. To handle this use case you can use BodyCodec.jsonStream. You pass a JSON parser that emits the read JSON streams from the HTTP response:

JsonParser parser = JsonParser.newParser().objectValueMode();
parser.handler(event -> {
JsonObject object = event.objectValue();
System.out.println("Got " + object.encode());
});
client
.get(8080, "myserver.mycompany.com", "/some-uri")
.as(BodyCodec.jsonStream(parser))
.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
Finally if you are not interested at all by the response content, the BodyCodec.none simply discards the entire response body

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.as(BodyCodec.none())
.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
When you don’t know in advance the content type of the http response, you can still use the bodyAsXXX() methods that decode the response to a specific type

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.send()
.onSuccess(res -> {
// Decode the body as a json object
JsonObject body = res.bodyAsJsonObject();

    System.out.println(
      "Received response with status code" +
        res.statusCode() +
        " with body " +
        body);
})
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
this is only valid for the response decoded as a buffer.
Server-Sent Events
Using a specific body codec: SseBodyCodec, you can decode a Server-Sent Events stream into a list of events:

WebClient client = WebClient.create(vertx, new WebClientOptions().setDefaultPort(servicePort).setDefaultHost("localhost"));
client.get("/basic?count=5").as(BodyCodec.sseStream(stream -> {
stream.handler(v -> System.out.println("Event received " + v));
stream.endHandler(v ->  System.out.println("End of stream " + v));
})).send().expecting(HttpResponseExpectation.SC_OK)
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
Response expectations
By default, a Vert.x Web Client request ends with an error only if something wrong happens at the network level.

In other words, you must perform sanity checks manually after the response is received:

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.send()
.onSuccess(res -> {
if (
res.statusCode() == 200 &&
res.getHeader("content-type").equals("application/json")) {
// Decode the body as a json object
JsonObject body = res.bodyAsJsonObject();

      System.out.println(
        "Received response with status code" +
          res.statusCode() +
          " with body " +
          body);
    } else {
      System.out.println("Something went wrong " + res.statusCode());
    }
})
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
You can trade flexibility for clarity and conciseness using response expectations.

Response expectations can fail a request when the response does not match a criteria.

The Web Client can reuse the Vert.x HTTP Client predefined expectations:

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.send()
.expecting(HttpResponseExpectation.SC_SUCCESS.and(HttpResponseExpectation.JSON))
.onSuccess(res -> {
// Safely decode the body as a json object
JsonObject body = res.bodyAsJsonObject();
System.out.println(
"Received response with status code" +
res.statusCode() +
" with body " +
body);
})
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
You can also create custom expectations when existing expectations don’t fit your needs:

Expectation<HttpResponseHead> methodsPredicate = new Expectation<HttpResponseHead>() {
@Override
public boolean test(HttpResponseHead resp) {
String methods = resp.getHeader("Access-Control-Allow-Methods");
return methods != null && methods.contains("POST");
}
};

// Send pre-flight CORS request
client
.request(
HttpMethod.OPTIONS,
8080,
"myserver.mycompany.com",
"/some-uri")
.putHeader("Origin", "Server-b.com")
.putHeader("Access-Control-Request-Method", "POST")
.send()
.expecting(methodsPredicate)
.onSuccess(res -> {
// Process the POST request now
})
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
Predefined expectations
As a convenience, the Vert.x HTTP Client ships a few expectations for common uses cases that also applies to the Web Client.

For status codes, e.g. HttpResponseExpectation.SC_SUCCESS to verify that the response has a 2xx code, you can also create a custom one:

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.send()
.expecting(HttpResponseExpectation.status(200, 202))
.onSuccess(res -> {
// ....
});
For content types, e.g. HttpResponseExpectation.JSON to verify that the response body contains JSON data, you can also create a custom one:

client
.get(8080, "myserver.mycompany.com", "/some-uri")
.send()
.expecting(HttpResponseExpectation.contentType("some/content-type"))
.onSuccess(res -> {
// ....
});
Please refer to the HttpResponseExpectation documentation for a full list of predefined predicates.

Creating custom failures
By default, response expectations (including the predefined ones) use a default error converter which discards the body and conveys a simple message. You can customize the exception class by mapping the failure:

Expectation<HttpResponseHead> expectation = HttpResponseExpectation.SC_SUCCESS
.wrappingFailure((resp, err) -> new MyCustomException(err.getMessage()));
Many web APIs provide details in error responses. For example, the Marvel API uses this JSON object format:

{
"code": "InvalidCredentials",
"message": "The passed API key is invalid."
}
To avoid losing this information, it is possible to transform response body:

HttpResponseExpectation.SC_SUCCESS.wrappingFailure((resp, err) -> {
// Invoked after the response body is fully received
HttpResponse<?> response =(HttpResponse<?>) resp;

if (response
.getHeader("content-type")
.equals("application/json")) {

    // Error body is JSON data
    JsonObject body = response.bodyAsJsonObject();

    return new MyCustomException(
      body.getString("code"),
      body.getString("message"));
}

// Fallback to defaut message
return new MyCustomException(err.getMessage());
});
creating exception in Java can have a performance cost when it captures a stack trace, so you might want to create exceptions that do not capture the stack trace. By default, exceptions are reported using an exception that does not capture the stack trace.
Handling 30x redirections
By default the client follows redirections, you can configure the default behavior in the WebClientOptions:

WebClient client = WebClient
.create(vertx, new WebClientOptions().setFollowRedirects(false));
The client will follow at most 16 requests redirections, it can be changed in the same options:

WebClient client = WebClient
.create(vertx, new WebClientOptions().setMaxRedirects(5));
For security reason, client won’t follow redirects for request with methods different from GET or HEAD
Client side load balancing
By default, when the client resolves a hostname to a list of several IP addresses, the client uses the first returned IP address.

The client can be configured to perform client side load balancing instead

WebClient client = WebClient.wrap(vertx
.httpClientBuilder()
.withLoadBalancer(LoadBalancer.ROUND_ROBIN)
.build());
Vert.x provides out of the box several load balancing policies you can use

Round-robin

Least requests

Power of two choices

Consistent hashing

Most load balancing policies are pretty much self-explanatory.

Hash based routing can be achieved with the LoadBalancer.CONSISTENT_HASHING policy.

WebClient client = WebClient.wrap(vertx
.httpClientBuilder()
.withLoadBalancer(LoadBalancer.ROUND_ROBIN)
.build());
You can read more details about client side load balancing in the Vert.x Core HTTP client documentation.

HTTP response caching
Vert.x web offers an HTTP response caching facility; to use it, you create a CachingWebClient.

Creating a caching web client
WebClient client = WebClient.create(vertx);
WebClient cachingWebClient = CachingWebClient.create(client);
Configuring what is cached
By default, a caching web client will only cache a response from a GET method that has a status code of 200, 301, or 404. Additionally, responses that contain a Vary header will not be cached by default.

This can be configured by passing CachingWebClientOptions during client creation.

CachingWebClientOptions options = new CachingWebClientOptions()
.addCachedMethod(HttpMethod.HEAD)
.removeCachedStatusCode(301)
.setEnableVaryCaching(true);

WebClient client = WebClient.create(vertx);
WebClient cachingWebClient = CachingWebClient.create(client, options);
Responses that contain the private directive in the Cache-Control header will not be cached unless the client is also a WebClientSession. See Handling private responses.

Providing an external store
When storing responses, the default caching client will use a local Map.You may provide your own store implementation to store responses. To do so, implement CacheStore, and then you can provide it when creating your client.

WebClient client = WebClient.create(vertx);
CacheStore store = new NoOpCacheStore(); // or any store you like
WebClient cachingWebClient = CachingWebClient.create(client, store);
Handling private responses
To enable private response caching, the CachingWebClient can be combined with the WebClientSession. When this is done, public responses, those with the public directive in the Cache-Control header, will be cached in the CacheStore the client was created with. Private responses, those with the private directive in the Cache-Control header, will be cached in with the session to ensure the cached response is not leaked to other users (sessions).

To create a client that can cache private responses, pass a CachingWebClient to a WebClientSession.

WebClient client = WebClient.create(vertx);
WebClient cachingWebClient = CachingWebClient.create(client);
WebClient sessionClient = WebClientSession.create(cachingWebClient);
URI templates
URI templates provide an alternative to HTTP request string URIs based on the URI Template RFC 6570.

You can read the Vert.x URI template documentation to learn more about it.

You can create a HttpRequest with a UriTemplate URI instead of a Java string URI

first parse the template string to a UriTemplate

UriTemplate REQUEST_URI = UriTemplate.of("/some-uri?{param}");
then use it to create a request

HttpRequest<Buffer> request = client.get(8080, "myserver.mycompany.com", REQUEST_URI);
set the template parameter

request.setTemplateParam("param", "param_value");
and finally send the request as usual

request.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
or fluently

client.get(8080, "myserver.mycompany.com", REQUEST_URI)
.setTemplateParam("param", "param_value")
.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
URI templates expansion
Before sending the request, Vert.x WebClient expands the template to a string with the request template parameters.

String expansion takes care of encoding the parameters for you,

String euroSymbol = "\u20AC";
UriTemplate template = UriTemplate.of("/convert?{amount}&{currency}");

// Request uri: /convert?amount=1234&currency=%E2%82%AC
Future<HttpResponse<Buffer>> fut = client.get(template)
.setTemplateParam("amount", amount)
.setTemplateParam("currency", euroSymbol)
.send();
The default expansion syntax is known as simple string expansion, there are other expansion syntax available

path segment expansion ({/varname})

form-style query expansion ({?varname})

etc…​

You can refer to the Vert.x URI Template documentation (add link when available) for a complete overview of the various expansion styles.

As mandated by the RFC, template expansion will replace missing template parameters by empty strings. You can change this behavior to fail instead:

WebClient webClient = WebClient.create(vertx, new WebClientOptions()
.setTemplateExpandOptions(new ExpandOptions()
.setAllowVariableMiss(false))
);
Template parameter values
Template parameters accept String, List<String> and Map<String, String> values.

The expansion of each kind depends on the expansion style (denoted by the ? prefix) , here is an example of the query parameter that is exploded (denoted by the * suffix) and expanded using form-style query expansion:

Map<String, String> query = new HashMap<>();
query.put("color", "red");
query.put("width", "30");
query.put("height", "50");
UriTemplate template = UriTemplate.of("/{?query*}");

// Request uri: /?color=red&width=30&height=50
Future<HttpResponse<Buffer>> fut = client.getAbs(template)
.setTemplateParam("query", query)
.send();
Form-style query expansion expands the variable {?query*} as ?color=red&width=30&height=50 per definition.

Using HTTPS
Vert.x Web Client can be configured to use HTTPS in exactly the same way as the Vert.x HttpClient.

You can specify the behavior per request

client
.get(443, "myserver.mycompany.com", "/some-uri")
.ssl(true)
.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
Or using create methods with absolute URI argument

client
.getAbs("https://myserver.mycompany.com:4043/some-uri")
.send()
.onSuccess(res ->
System.out.println("Received response with status code" + res.statusCode()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));
Sessions management
Vert.x web offers a web session management facility; to use it, you create a WebClientSession for every user (session) and use it instead of the WebClient.

Creating a WebClientSession
You create a WebClientSession instance as follows

WebClient client = WebClient.create(vertx);
WebClientSession session = WebClientSession.create(client);
Making requests
Once created, a WebClientSession can be used instead of a WebClient to do HTTP(s) requests and automatically manage any cookies received from the server(s) you are calling.

Setting session level headers
You can set any session level headers to be added to every request as follows:

WebClientSession session = WebClientSession.create(client);
session.addHeader("my-jwt-token", jwtToken);
The headers will then be added to every request; notice that these headers will be sent to all hosts; if you need to send different headers to different hosts, you have to add them manually to every single request and not to the WebClientSession.

OAuth2 security
Vert.x web offers a web session management facility; to use it, you create a OAuth2WebClient for every user (session) and use it instead of the WebClient.

Creating an Oauth2 Client
You create a OAuth2WebClient instance as follows

WebClient client = WebClient.create(vertx);
OAuth2WebClient oauth2 = OAuth2WebClient.create(
client,
OAuth2Auth.create(vertx, new OAuth2Options(/* enter IdP config */)))

// configure the initial credentials (needed to fetch if needed
// the access_token
.withCredentials(new TokenCredentials("some.jwt.token"));
Client’s can also take advantage of OpenId Service discovery to fully configure the client, for example to connect to a real keycloak server one can just do:

KeycloakAuth.discover(
vertx,
new OAuth2Options().setSite("https://keycloakserver.com"))
.onSuccess(oauth -> {
OAuth2WebClient client = OAuth2WebClient.create(
WebClient.create(vertx),
oauth)
// if your keycloak is configured for password_credentials_flow
.withCredentials(
new UsernamePasswordCredentials("bob", "s3cret"));
});
Making requests
Once created, a OAuth2WebClient can be used instead of a WebClient to do HTTP(s) requests and automatically manage any cookies received from the server(s) you are calling.

Avoid expired tokens
You can set token expiration leeway to every request as follows:

OAuth2WebClient client = OAuth2WebClient.create(
baseClient,
oAuth2Auth,
new OAuth2WebClientOptions()
.setLeeway(5));
If a request is to be performed the current active user object is checked for expiration with the extra given leeway. This will allow the client to perform a token refresh if needed, instead of aborting the operation with an error.

Request may still fail due to expired tokens since the expiration calculation will still be performed at the server side. To reduce the work on the user side, the client can be configured to perform a single retry on requests that return status code 401 (Forbidden). When the option flag: refreshTokenOnForbidden is set to true, then the client will perform a new token request retry the original request before passing the response to the user handler/promise.

OAuth2WebClient client = OAuth2WebClient.create(
baseClient,
oAuth2Auth,
new OAuth2WebClientOptions()
// the client will attempt a single token request, if the request
// if the status code of the response is 401
// there will be only 1 attempt, so the second consecutive 401
// will be passed down to your handler/promise
.setRenewTokenOnForbidden(true));
RxJava 3 API
The RxJava HttpRequest provides an rx-ified version of the original API, the rxSend method returns a Single<HttpResponse<Buffer>> that makes the HTTP request upon subscription, as consequence, the Single can be subscribed many times.

Single<HttpResponse<Buffer>> single = client
.get(8080, "myserver.mycompany.com", "/some-uri")
.rxSend();

// Send a request upon subscription of the Single
single.subscribe(response -> System.out.println("Received 1st response with status code" + response.statusCode()), error -> System.out.println("Something went wrong " + error.getMessage()));

// Send another request
single.subscribe(response -> System.out.println("Received 2nd response with status code" + response.statusCode()), error -> System.out.println("Something went wrong " + error.getMessage()));
The obtained Single can be composed and chained naturally with the RxJava API

Single<String> url = client
.get(8080, "myserver.mycompany.com", "/some-uri")
.rxSend()
.map(HttpResponse::bodyAsString);

// Use the flatMap operator to make a request on the URL Single
url
.flatMap(u -> client.getAbs(u).rxSend())
.subscribe(response -> System.out.println("Received response with status code" + response.statusCode()), error -> System.out.println("Something went wrong " + error.getMessage()));
The same APIs are available:

Single<HttpResponse<JsonObject>> single = client
.get(8080, "myserver.mycompany.com", "/some-uri")
.putHeader("some-header", "header-value")
.addQueryParam("some-param", "param value")
.as(BodyCodec.jsonObject())
.rxSend();
single.subscribe(resp -> {
System.out.println(resp.statusCode());
System.out.println(resp.body());
});
The rxSendStream shall be preferred for sending bodies Flowable<Buffer>.

Flowable<Buffer> body = getPayload();

Single<HttpResponse<Buffer>> single = client
.post(8080, "myserver.mycompany.com", "/some-uri")
.rxSendStream(body);
single.subscribe(resp -> {
System.out.println(resp.statusCode());
System.out.println(resp.body());
});
Upon subscription, the body will be subscribed and its content used for the request.

HTTP Response Expectations
Interacting with an HTTP backend often involves verifying HTTP response codes and/or content types.

To streamline the process of verification, use the HttpResponseExpectation methods:

Single<HttpResponse<Buffer>> single = client
.get(8080, "myserver.mycompany.com", "/some-uri")
.rxSend()
// Transforms the single into a failed single if the HTTP response is not successful
.compose(HttpResponseExpectation.status(200))
// Transforms the single into a failed single if the HTTP response content is not JSON
.compose(HttpResponseExpectation.contentType("application/json"));
Unix domain sockets
The Web Client supports Unix domain sockets. For example, you can interact with the local Docker daemon.

To achieve this, you must run your application with JDK16+ or create the Vertx instance using a native transport.

SocketAddress serverAddress = SocketAddress
.domainSocketAddress("/var/run/docker.sock");

// We still need to specify host and port so the request
// HTTP header will be localhost:8080
// otherwise it will be a malformed HTTP request
// the actual value does not matter much for this example
client
.request(
HttpMethod.GET,
serverAddress,
8080,
"localhost",
"/images/json")
.as(BodyCodec.jsonObject())
.send()
.expecting(HttpResponseExpectation.SC_ACCEPTED)
.onSuccess(res ->
System.out.println("Current Docker images" + res.body()))
.onFailure(err ->
System.out.println("Something went wrong " + err.getMessage()));