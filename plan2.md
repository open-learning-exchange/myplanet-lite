Wait, if `imagePaths` are just strings with resource IDs, could they be attachments from CouchDB `db/resources`?
If `imagePath` looks like `/db/resources/some_id/image.jpg`, then instead of downloading each image attachment sequentially (or concurrently but individually), can we query the `db/resources` metadata in bulk to get info? No, we still need to download the image file.

But wait. If `imagePaths` are just URLs, we can't really bulk download them via HTTP. Is it possible we can fetch them using a single API call? Or maybe we can just query CouchDB `db/resources/_all_docs` with `keys=[id1, id2]` but that returns JSON documents, not the attachment bytes!

Let's read `resolveImageUrl` again.
It builds a URL from the `imagePath`. For example: `http://localhost/db/resources/xyz/image.jpg`.

Wait, the task says: "N+1 Query Issue in `loadEditInitialImages`
... Grouping these requests or using a bulk endpoint (if available) would be better. Giving confidence 2 since changing this requires API support."
Since I am modifying the client, I can't add a new API to CouchDB. So what CAN I do if I can't change the API?
"If none exist, create a focused benchmark or performance measurement for this code path
⚠️ If you cannot measure the performance impact (or it is impractical to do so), document why and your rationale for why this change is a net performance improvement."

Wait! If we download them concurrently using OkHttp, each `async` block makes a new network call.
But wait! If they are CouchDB attachments, they are part of CouchDB documents.
Does CouchDB have an API to download attachments in bulk?
CouchDB 3.x: you can use `GET /db/_all_docs?include_docs=true&attachments=true`.
This will include base64 encoded attachments inside the JSON!
If we get them with `attachments=true`, we get the images in base64.
Is `imagePath` just an ID, or a full path?
Let's look at `extractFileName` and `parseResourceFromPath`.
