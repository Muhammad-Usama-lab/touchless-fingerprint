# RMST Biometrics API Documentation

## Overview

REST API for fingerprint image processing and WSQ conversion.

- **Base URL:** `http://<server-ip>:8000`
- **Interactive Docs:** `http://<server-ip>:8000/docs`
- **Content Type:** `multipart/form-data` for uploads, `application/json` for responses

---

## Quick Start

### Start the Server

```bash
# Activate virtual environment
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Run server
python3 server.py
```

### Server Output

```
  ╔══════════════════════════════════════════════════╗
  ║       RMST Biometrics Server Started             ║
  ╠══════════════════════════════════════════════════╣
  ║  Local:   http://localhost:8000                  ║
  ║  Network: http://192.168.1.100:8000              ║
  ║  Docs:    http://192.168.1.100:8000/docs         ║
  ╠══════════════════════════════════════════════════╣
  ║  File retention: 1 hour(s)                       ║
  ╚══════════════════════════════════════════════════╝
```

---

## Endpoints

### 1. Process Images

Process multiple fingerprint images and convert to PNG + WSQ format.

**Endpoint:** `POST /process-images`

#### Request

| Parameter | Type | Location | Required | Default | Description |
|-----------|------|----------|----------|---------|-------------|
| `files` | File[] | form-data | Yes | - | Fingerprint images to process |
| `thickness` | int | query | No | 2 | Ridge thickness: 1=thin, 2=medium, 3=thick |
| `margin` | int | query | No | 20 | Edge mask margin in pixels (0 to disable) |
| `crop` | bool | query | No | true | Crop output to fingerprint region |
| `dpi` | int | query | No | 500 | Output DPI (100-1000) |
| `block_size` | int | query | No | 16 | Processing block size |

#### Response

```json
{
  "success": true,
  "message": "Successfully processed 2 image(s)",
  "processed": [
    {
      "original_name": "RIGHT_INDEX.png",
      "processed_png": "http://192.168.1.100:8000/files/abc123_RIGHT_INDEX_processed.png",
      "processed_wsq": "http://192.168.1.100:8000/files/abc123_RIGHT_INDEX.wsq"
    },
    {
      "original_name": "RIGHT_MIDDLE.png",
      "processed_png": "http://192.168.1.100:8000/files/abc123_RIGHT_MIDDLE_processed.png",
      "processed_wsq": "http://192.168.1.100:8000/files/abc123_RIGHT_MIDDLE.wsq"
    }
  ],
  "batch_id": "abc123",
  "parameters": {
    "thickness": 2,
    "margin": 20,
    "crop": true,
    "dpi": 500,
    "block_size": 16
  }
}
```

#### Error Response

```json
{
  "detail": "Failed to process RIGHT_INDEX.png: <error message>"
}
```

---

### 2. Download File

Download a processed file (PNG or WSQ).

**Endpoint:** `GET /files/{filename}`

#### Parameters

| Parameter | Type | Location | Description |
|-----------|------|----------|-------------|
| `filename` | string | path | Name of the file to download |

#### Response

- **Success:** File binary with appropriate `Content-Type`
  - PNG: `image/png`
  - WSQ: `application/octet-stream`
- **Error (404):** `{"detail": "File not found"}`

---

### 3. Delete Batch

Delete all files from a specific processing batch.

**Endpoint:** `DELETE /files/{batch_id}`

#### Parameters

| Parameter | Type | Location | Description |
|-----------|------|----------|-------------|
| `batch_id` | string | path | Batch ID returned from process-images |

#### Response

```json
{
  "success": true,
  "deleted": 4
}
```

---

### 4. Health Check

Check if the server is running.

**Endpoint:** `GET /health`

#### Response

```json
{
  "status": "healthy",
  "timestamp": "2026-01-14T10:30:00.000000"
}
```

---

## Usage Examples

### cURL

#### Basic Processing (Default Parameters)

```bash
curl -X POST http://192.168.1.100:8000/process-images \
  -F "files=@finger1.png" \
  -F "files=@finger2.png"
```

#### Custom Parameters

```bash
curl -X POST "http://192.168.1.100:8000/process-images?thickness=3&margin=25&crop=true&dpi=500" \
  -F "files=@finger1.png" \
  -F "files=@finger2.png"
```

#### Download Processed File

```bash
curl -O http://192.168.1.100:8000/files/abc123_finger1_processed.png
curl -O http://192.168.1.100:8000/files/abc123_finger1.wsq
```

#### Delete Batch

```bash
curl -X DELETE http://192.168.1.100:8000/files/abc123
```

---

### Kotlin (OkHttp)

```kotlin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

val client = OkHttpClient()

// Process images
fun processFingerprints(imageFiles: List<File>, thickness: Int = 2, margin: Int = 20): String {
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .apply {
            imageFiles.forEach { file ->
                addFormDataPart(
                    "files",
                    file.name,
                    file.asRequestBody("image/png".toMediaType())
                )
            }
        }
        .build()

    val request = Request.Builder()
        .url("http://192.168.1.100:8000/process-images?thickness=$thickness&margin=$margin")
        .post(requestBody)
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Failed: ${response.code}")
        return response.body?.string() ?: ""
    }
}

// Download file
fun downloadFile(url: String, outputFile: File) {
    val request = Request.Builder().url(url).build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Failed: ${response.code}")
        response.body?.byteStream()?.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
```

---

### Kotlin (Retrofit)

#### API Interface

```kotlin
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface BiometricsApi {

    @Multipart
    @POST("process-images")
    suspend fun processImages(
        @Part files: List<MultipartBody.Part>,
        @Query("thickness") thickness: Int = 2,
        @Query("margin") margin: Int = 20,
        @Query("crop") crop: Boolean = true,
        @Query("dpi") dpi: Int = 500
    ): Response<ProcessResponse>

    @GET("files/{filename}")
    suspend fun downloadFile(
        @Path("filename") filename: String
    ): Response<ResponseBody>

    @DELETE("files/{batchId}")
    suspend fun deleteBatch(
        @Path("batchId") batchId: String
    ): Response<DeleteResponse>

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
}
```

#### Data Classes

```kotlin
data class ProcessResponse(
    val success: Boolean,
    val message: String,
    val processed: List<ProcessedFile>,
    val batch_id: String,
    val parameters: ProcessingParams
)

data class ProcessedFile(
    val original_name: String,
    val processed_png: String,
    val processed_wsq: String
)

data class ProcessingParams(
    val thickness: Int,
    val margin: Int,
    val crop: Boolean,
    val dpi: Int,
    val block_size: Int
)

data class DeleteResponse(
    val success: Boolean,
    val deleted: Int
)

data class HealthResponse(
    val status: String,
    val timestamp: String
)
```

#### Usage

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("http://192.168.1.100:8000/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val api = retrofit.create(BiometricsApi::class.java)

// Process fingerprints
suspend fun processFingerprints(imageFiles: List<File>) {
    val parts = imageFiles.map { file ->
        val requestBody = file.asRequestBody("image/png".toMediaType())
        MultipartBody.Part.createFormData("files", file.name, requestBody)
    }

    val response = api.processImages(
        files = parts,
        thickness = 2,
        margin = 20
    )

    if (response.isSuccessful) {
        response.body()?.processed?.forEach { file ->
            println("PNG: ${file.processed_png}")
            println("WSQ: ${file.processed_wsq}")
        }
    }
}
```

---

### Python (requests)

```python
import requests

BASE_URL = "http://192.168.1.100:8000"

# Process images
def process_fingerprints(image_paths, thickness=2, margin=20):
    files = [("files", open(path, "rb")) for path in image_paths]
    params = {"thickness": thickness, "margin": margin}

    response = requests.post(
        f"{BASE_URL}/process-images",
        files=files,
        params=params
    )
    return response.json()

# Download file
def download_file(url, output_path):
    response = requests.get(url)
    with open(output_path, "wb") as f:
        f.write(response.content)

# Example usage
result = process_fingerprints(["finger1.png", "finger2.png"], thickness=2)
print(result)

for item in result["processed"]:
    download_file(item["processed_png"], f"output/{item['original_name']}")
    download_file(item["processed_wsq"], f"output/{item['original_name']}.wsq")
```

---

## Processing Parameters Guide

| Parameter | Value | Effect |
|-----------|-------|--------|
| `thickness=1` | Thin | Single-pixel ridges (for minutiae extraction) |
| `thickness=2` | Medium | Balanced visibility (recommended) |
| `thickness=3` | Thick | Bold ridges (ink-like appearance) |
| `margin=0` | Disabled | No edge masking |
| `margin=20` | Default | Removes noisy corners |
| `margin=30+` | Aggressive | More edge removal |
| `crop=true` | Enabled | Output cropped to fingerprint only |
| `crop=false` | Disabled | Keep original dimensions |
| `dpi=500` | Standard | NADRA/FBI biometric compliance |

---

## File Retention

- Processed files are automatically deleted after **1 hour**
- Use `DELETE /files/{batch_id}` to manually delete files earlier
- Cleanup runs every 10 minutes

---

## Error Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 400 | Bad request (invalid parameters) |
| 404 | File not found |
| 500 | Processing error |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_HOST` | 0.0.0.0 | Server bind address |
| `SERVER_PORT` | 8000 | Server port |
| `BASE_URL` | auto-detect | Base URL for file links |

```bash
# Custom configuration
SERVER_PORT=9000 BASE_URL=http://myserver.com:9000 python3 server.py
```
