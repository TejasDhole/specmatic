package run.qontract.core

import run.qontract.core.utilities.URIUtils.parseQuery
import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value
import io.netty.buffer.ByteBuf
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

data class HttpRequest(var method: String? = null, var path: String? = null, val headers: HashMap<String, String> = HashMap(), var body: Value? = EmptyString, var queryParams: HashMap<String, String> = HashMap(), val formFields: Map<String, String> = emptyMap()) {
    fun updateQueryParams(queryParams: Map<String, String>): HttpRequest {
        this.queryParams.putAll(queryParams)
        return this
    }

    fun updatePath(path: String): HttpRequest {
        try {
            val urlParam = URI(path)
            updateWith(urlParam)
        } catch (e: URISyntaxException) {
            this.path = path
        } catch (e: UnsupportedEncodingException) {
            this.path = path
        }
        return this
    }

    fun updateQueryParam(key: String, value: String): HttpRequest {
        queryParams[key] = value
        return this
    }

    fun updateBody(body: Value): HttpRequest {
        this.body = body
        return this
    }

    fun updateBody(body: String?): HttpRequest {
        this.body = parsedValue(body)
        return this
    }

    fun updateWith(url: URI) {
        path = url.path
        queryParams = parseQuery(url.query)
    }

    fun updateMethod(name: String): HttpRequest {
        method = name.toUpperCase()
        return this
    }

    private fun updateBody(contentBuffer: ByteBuf) {
        val bodyString = contentBuffer.toString(Charset.defaultCharset())
        updateBody(bodyString)
    }

    fun updateHeader(key: String, value: String): HttpRequest {
        headers[key] = value
        return this
    }

    val bodyString: String
        get() = body.toString()

    fun getURL(baseURL: String?): String =
        "$baseURL$path" + if (queryParams.isNotEmpty()) {
            val joinedQueryParams =
                    queryParams.toList()
                            .joinToString("&") {
                                "${it.first}=${URLEncoder.encode(it.second, StandardCharsets.UTF_8.toString())}"
                            }
            "?$joinedQueryParams"
        } else ""

    fun toJSON(): Map<String, Value> {
        val requestMap = mutableMapOf<String, Value>()

        requestMap["path"] = path?.let { StringValue(it) } ?: StringValue("/")
        method?.let { requestMap["method"] = StringValue(it) } ?: throw ContractException("Can't serialise the request without a method.")
        body?.let { requestMap["body"] = it }

        if (queryParams.size > 0) requestMap["query"] = JSONObjectValue(queryParams.mapValues { StringValue(it.value) })
        if (headers.size > 0) requestMap["headers"] = JSONObjectValue(headers.mapValues { StringValue(it.value) })

        if(formFields.isNotEmpty()) requestMap["form-fields"] = JSONObjectValue(formFields.mapValues { StringValue(it.value) })

        return requestMap
    }

    fun setHeaders(addedHeaders: Map<String, String>): HttpRequest {
        headers.putAll(addedHeaders)
        return this
    }

    fun toLogString(prefix: String = ""): String {
        val methodString = method ?: "NO_METHOD"

        val pathString = path ?: "NO_PATH"
        val queryParamString = queryParams.map { "${it.key}=${it.value}"}.joinToString("&").let { if(it.isNotEmpty()) "?$it" else it }
        val urlString = "$pathString$queryParamString"

        val firstLine = "$methodString $urlString"
        val headerString = headers.map { "${it.key}: ${it.value}" }.joinToString("\n")
        val bodyString = when {
            formFields.isNotEmpty() -> formFields.map { "${it.key}=${it.value}"}.joinToString("&")
            else -> body.toString()
        }

        val firstPart = listOf(firstLine, headerString).joinToString("\n").trim()
        val requestString = listOf(firstPart, "", bodyString).joinToString("\n")
        return startLinesWith(requestString, prefix)
    }
}

fun s(json: Map<String, Value>, key: String): String = (json.getValue(key) as StringValue).string

fun requestFromJSON(json: Map<String, Value>): HttpRequest {
    var httpRequest = HttpRequest()
    httpRequest = httpRequest.updateMethod(s(json, "method"))
    httpRequest = httpRequest.updatePath(if ("path" in json) s(json, "path") else "/")
    httpRequest = httpRequest.updateQueryParams(if ("query" in json)
        (json["query"] as JSONObjectValue).jsonObject.mapValues { it.value.toString() }
    else emptyMap())
    httpRequest = httpRequest.setHeaders(if ("headers" in json) (json["headers"] as JSONObjectValue).jsonObject.mapValues { it.value.toString() } else emptyMap())

    if("form-fields" in json) {
        val formFields = json.getValue("form-fields")
        if(formFields !is JSONObjectValue)
            throw ContractException("form-fields must be a json object.")

        httpRequest = httpRequest.copy(formFields = formFields.jsonObject.mapValues { it.value.toStringValue() })
    }
    else if("body" in json)
        httpRequest = httpRequest.updateBody(json.getValue("body"))

    return httpRequest
}

internal fun startLinesWith(str: String, startValue: String): String {
    return str.split("\n").map { "$startValue$it" }.joinToString("\n")
}

