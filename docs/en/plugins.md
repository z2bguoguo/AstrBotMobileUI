# Client plugin development

A client plugin is a JSON action manifest, not an AstrBot Python plugin. No downloaded DEX or native code is executed. Import it from the client plugin screen.

```json
{
  "apiVersion": 1,
  "name": "Runtime Inspector",
  "description": "Read the current server version",
  "operations": [
    {
      "title": "Version",
      "method": "GET",
      "path": "/api/v1/stats/version",
      "pathParams": {},
      "query": {},
      "body": {}
    }
  ]
}
```

The manifest must be at most 256 KiB and contain at most 50 operations. Methods are GET, POST, PUT, PATCH and DELETE. Paths must stay under the selected instance's `/api/`; external URLs, query strings in the path and traversal are rejected. Use path placeholders and the `pathParams` object rather than concatenating values.

Operations run with the selected connection's permissions. Writes require confirmation. Document supported server versions and required scopes. Test success, unauthorized, forbidden, missing endpoints and `status:error` envelopes. Avoid blind write retries. Version 1 has no automatic route conversion or arbitrary background code.

See `examples/runtime-info.json` for an importable example. Server HTML plugins are separate and rely on the server's token-scoped content URL.
