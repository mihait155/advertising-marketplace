# 0004. Scan uploads before storing them, and strip metadata

Status: accepted

## Context

Users embed photos in postings and messages, so files from one user are served to others. Uploaded files
can carry malware or tracking content, and photos often carry GPS coordinates in EXIF.

## Decision

The pipeline runs in a fixed order, and nothing is written to storage until every step passed:

1. Size limit (10 MB) and a per-user hourly limit.
2. ClamAV `INSTREAM` scan of the full bytes. Infected → `422`. Scanner unreachable → `503`: **fail closed**.
3. File type from magic bytes (JPEG, PNG, GIF, WebP). SVG is refused because it can contain script.
4. Metadata stripping: EXIF/XMP/IPTC/comments removed; JPEG orientation and ICC colour profiles kept.
5. Store the stripped bytes in a **private** bucket; serve them through 1-hour signed URLs.

External image URLs in Markdown are rejected, so every displayed image went through this pipeline.

## Consequences

- An outage of ClamAV stops uploads. That's deliberate and covered by an alert, rather than silently
  accepting unscanned files.
- Stripping is a byte-level rewrite of container formats, not a re-encode, so image quality is unchanged and
  it's fast. It doesn't sanitise pixel data (polyglot payloads hidden in pixels would survive). Storing each
  object with the `Content-Type` detected from its magic bytes, refusing SVG, and serving only through
  short-lived signed URLs limits what those could do.
- Uploads that are never embedded are deleted after 24 hours.

## Alternatives considered

- **Store first, scan asynchronously.** Faster uploads, but unscanned files exist in the bucket and could be
  served in the meantime.
- **Re-encode every image.** Strips everything including pixel tricks, but it costs CPU, loses quality, and
  needs an imaging library with its own attack surface.
