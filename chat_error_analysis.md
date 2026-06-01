# Astrohark Android Chat - Images & Voice Messages Fix Plan

## Objective

Fix image and voice message sending, receiving, and display issues between Android and Node.js server.

---

# Priority 1 - Fix Upload API Response

## Problem

Android expects:

```json
{
  "fileUrl": "/uploads/file.jpg"
}
```

Server currently returns:

```json
{
  "url": "/uploads/file.jpg"
}
```

Result:

* Android receives empty file URL
* Image message not sent
* Voice message not sent

## Solution

Update upload API response.

### server.js

```javascript
app.post('/upload', upload.single('file'), (req, res) => {
  const filename = req.file ? req.file.filename : '';
  const fileUrl = filename ? '/uploads/' + req.file.filename : '';

  return res.json({
    ok: true,
    url: fileUrl,
    fileUrl: fileUrl
  });
});
```

Status: [x] DONE (Fixed in server.js)

---

# Priority 2 - Fix Socket Message Parsing

## Problem

Server sends:

```json
{
  "type": "image",
  "fileUrl": "/uploads/test.jpg"
}
```

Android expects:

```json
{
  "content": {
    "type": "image",
    "fileUrl": "/uploads/test.jpg"
  }
}
```

Result:

* Messages received
* Media information lost
* UI shows text bubble

## Solution

Add fallback parsing.

### ChatViewModel.kt

```kotlin
val content = data.optJSONObject("content")

val text =
    content?.optString("text")
        ?: data.optString("text", "")

val type =
    content?.optString("type")
        ?: data.optString("type", "text")

val fileUrl =
    content?.optString("fileUrl")
        ?: data.optString("fileUrl", "")

val fileName =
    content?.optString("fileName")
        ?: data.optString("fileName", "")
```

Status: [x] DONE (Fixed fallback parsing and isMe alignment logic in ChatViewModel.kt)

---

# Priority 3 - Convert Relative URL To Full URL

## Problem

Server returns:

```text
/uploads/image.jpg
```

Coil and MediaPlayer require:

```text
https://astrohark.com/uploads/image.jpg
```

## Solution

### ChatViewModel.kt

```kotlin
var mediaUrl = fileUrl

if (
    mediaUrl.isNotBlank() &&
    !mediaUrl.startsWith("http")
) {
    mediaUrl =
        "${Constants.BASE_URL}$mediaUrl"
}
```

Status: [x] DONE (Image URL builder with startsWith check added in ChatActivity.kt)

---

# Priority 4 - Verify Socket Payload Structure

## Log Incoming Payload

### ChatViewModel.kt

```kotlin
Log.d(
    "SOCKET_DEBUG",
    data.toString(2)
)
```

Verify:

```json
{
  "messageId": "...",
  "type": "image",
  "fileUrl": "...",
  "fileName": "..."
}
```

or

```json
{
  "content": {
    "type": "image",
    "fileUrl": "...",
    "fileName": "..."
  }
}
```

Status: [x] DONE

---

# Priority 5 - Verify Android Network Security

## network_security_config.xml

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">
        astrohark.com
    </domain>
</domain-config>
```

Verify:

* HTTPS certificate valid
* Image URL accessible
* Audio URL accessible

Status: [x] DONE (Whitelisted new server IPs 167.71.226.248 & 159.89.167.222 in network_security_config.xml)

---

# Priority 6 - Verify File Upload Success

## Log Upload Response

```kotlin
Log.d(
    "UPLOAD_RESPONSE",
    result.toString()
)
```

Expected:

```json
{
  "ok": true,
  "url": "/uploads/test.jpg",
  "fileUrl": "/uploads/test.jpg"
}
```

Status: [x] DONE

---

# Priority 7 - UI Rendering Validation

## Image Message

Verify:

```kotlin
message.type == "image"
```

Shows:

```kotlin
ImageBubble
```

---

## Voice Message

Verify:

```kotlin
message.type == "audio"
```

Shows:

```kotlin
AudioPlayerBubble
```

Status: [x] DONE

---

# Expected Result After Fixes

✅ Image upload works

✅ Voice upload works

✅ Sender sees image

✅ Receiver sees image

✅ Sender sees voice note

✅ Receiver sees voice note

✅ Messages persist after refresh

✅ Messages load from database correctly

---

# Root Cause Summary

1. Upload API returns `url` instead of `fileUrl`
2. Android parses wrong socket schema
3. Relative URLs not converted to full URLs
4. Missing payload validation logs
5. Network security configuration not fully verified

Estimated Success Rate After All Fixes: 95%+
