package org.mozilla.fenix.helpers

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.source
import java.io.IOException
import java.io.InputStream

class SearchDispatcher: Dispatcher()  {
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    override fun dispatch(request: RecordedRequest): MockResponse {
        val assetManager = InstrumentationRegistry.getInstrumentation().context.assets
        try {
            if (request.path!!.contains("searchPage.html")) {
                val pathWithoutQueryParams = Uri.parse(request.path!!.drop(1)).path
                assetManager.open(pathWithoutQueryParams!!).use { inputStream ->
                    return fileToResponse(inputStream)
                }
            }
            if (request.path!!.contains("search=testapp")) {
                // return MockResponse().setBody("searchResults.html") // Use regex to identify search term. Not sure what the return should be. Test and experiment
                val path = "pages/searchResults.html"
                assetManager.open(path).use { inputStream ->
                    return fileToResponse(inputStream)
                }
            }
            if (request.path!!.contains("searchResults.html?search=%s")) {
                return MockResponse().setResponseCode(200)
            }
            return MockResponse().setResponseCode(404)
        } catch (e: IOException) { // e.g. file not found.
            // We're on a background thread so we need to forward the exception to the main thread.
            mainThreadHandler.postAtFrontOfQueue { throw e }
            return MockResponse().setResponseCode(HTTP_NOT_FOUND)
        }
    }
}

@Throws(IOException::class)
private fun fileToResponse(file: InputStream): MockResponse {
    return MockResponse()
        .setResponseCode(HTTP_OK)
        .setBody(fileToBytes(file)!!)
}

@Throws(IOException::class)
private fun fileToBytes(file: InputStream): Buffer? {
    val result = Buffer()
    result.writeAll(file.source())
    return result
}