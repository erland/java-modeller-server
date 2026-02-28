package info.isaksson.erland.modeller.server.ops;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.BadRequestException;

/**
 * Minimal, deterministic JSON Patch applier.
 *
 * <p>Supports a subset of RFC 6902 operations used for Phase 3 materialization:
 * <ul>
 *   <li>add</li>
 *   <li>remove</li>
 *   <li>replace</li>
 * </ul>
 *
 * <p>Patch format is an array of objects: {"op":"add|remove|replace","path":"/a/b/0", "value": ...}
 */
public final class JsonPatchApplier {

    private final ObjectMapper mapper;

    public JsonPatchApplier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode apply(JsonNode input, JsonNode patchArray) {
        if (patchArray == null || !patchArray.isArray()) {
            throw new BadRequestException("JSON_PATCH payload must be an array");
        }

        JsonNode current = input == null ? mapper.createObjectNode() : input.deepCopy();

        for (JsonNode opNode : patchArray) {
            if (opNode == null || !opNode.isObject()) {
                throw new BadRequestException("Each JSON_PATCH item must be an object");
            }
            String op = text(opNode, "op");
            String path = text(opNode, "path");
            JsonNode value = opNode.get("value");

            switch (op) {
                case "add" -> current = add(current, path, value);
                case "replace" -> current = replace(current, path, value);
                case "remove" -> current = remove(current, path);
                default -> throw new BadRequestException("Unsupported JSON_PATCH op: " + op);
            }
        }

        return current;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual() || v.asText().isBlank()) {
            throw new BadRequestException("Missing/invalid field: " + field);
        }
        return v.asText();
    }

    private JsonNode add(JsonNode root, String path, JsonNode value) {
        return set(root, path, value, true);
    }

    private JsonNode replace(JsonNode root, String path, JsonNode value) {
        return set(root, path, value, false);
    }

    private JsonNode remove(JsonNode root, String path) {
        PathParts parts = split(path);
        JsonNode parent = navigateToParent(root, parts);

        if (parent.isObject()) {
            ((ObjectNode) parent).remove(parts.lastToken);
            return root;
        }
        if (parent.isArray()) {
            int idx = parseIndex(parts.lastToken, ((ArrayNode) parent).size(), false);
            ((ArrayNode) parent).remove(idx);
            return root;
        }

        throw new BadRequestException("Cannot remove from non-container at path: " + path);
    }

    private JsonNode set(JsonNode root, String path, JsonNode value, boolean allowAdd) {
        if (value == null) {
            // JSON Patch requires value for add/replace.
            throw new BadRequestException("Missing field: value");
        }
        PathParts parts = split(path);
        JsonNode parent = navigateToParent(root, parts);

        if (parent.isObject()) {
            ObjectNode o = (ObjectNode) parent;
            if (!allowAdd && !o.has(parts.lastToken)) {
                throw new BadRequestException("Cannot replace missing path: " + path);
            }
            o.set(parts.lastToken, value);
            return root;
        }
        if (parent.isArray()) {
            ArrayNode a = (ArrayNode) parent;
            if ("-".equals(parts.lastToken)) {
                if (!allowAdd) {
                    throw new BadRequestException("Cannot replace '-' path: " + path);
                }
                a.add(value);
                return root;
            }
            int idx = parseIndex(parts.lastToken, a.size(), allowAdd);
            if (allowAdd && idx == a.size()) {
                a.add(value);
            } else {
                a.set(idx, value);
            }
            return root;
        }

        throw new BadRequestException("Cannot set on non-container at path: " + path);
    }

    private JsonNode navigateToParent(JsonNode root, PathParts parts) {
        JsonNode cur = root;
        for (String token : parts.parentTokens) {
            if (cur.isObject()) {
                JsonNode next = cur.get(token);
                if (next == null || next.isNull()) {
                    // Create intermediate objects deterministically.
                    ObjectNode created = mapper.createObjectNode();
                    ((ObjectNode) cur).set(token, created);
                    cur = created;
                } else {
                    cur = next;
                }
            } else if (cur.isArray()) {
                ArrayNode a = (ArrayNode) cur;
                int idx = parseIndex(token, a.size(), true);
                if (idx == a.size()) {
                    // Create a new intermediate object at end.
                    ObjectNode created = mapper.createObjectNode();
                    a.add(created);
                    cur = created;
                } else {
                    JsonNode next = a.get(idx);
                    if (next == null || next.isNull()) {
                        ObjectNode created = mapper.createObjectNode();
                        a.set(idx, created);
                        cur = created;
                    } else {
                        cur = next;
                    }
                }
            } else {
                throw new BadRequestException("Invalid path (non-container in middle): /" + String.join("/", parts.parentTokens));
            }
        }
        return cur;
    }

    private static int parseIndex(String token, int size, boolean allowEnd) {
        try {
            int idx = Integer.parseInt(token);
            if (idx < 0) {
                throw new BadRequestException("Array index must be >= 0");
            }
            if (idx > size || (!allowEnd && idx >= size)) {
                throw new BadRequestException("Array index out of bounds: " + idx);
            }
            return idx;
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid array index: " + token);
        }
    }

    private static PathParts split(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new BadRequestException("Invalid JSON pointer path: " + path);
        }
        // Validate pointer syntax (but tokenize ourselves)
        try {
            JsonPointer.compile(path);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid JSON pointer path: " + path);
        }

        String[] raw = path.substring(1).split("/", -1);
        java.util.List<String> tokens = new java.util.ArrayList<>(raw.length);
        for (String r : raw) {
            // Unescape JSON Pointer (~1 -> /, ~0 -> ~)
            String t = r.replace("~1", "/").replace("~0", "~");
            tokens.add(t);
        }
        if (tokens.isEmpty()) {
            throw new BadRequestException("Path must not be root");
        }
        String last = tokens.remove(tokens.size() - 1);
        return new PathParts(tokens, last);
    }

    private record PathParts(java.util.List<String> parentTokens, String lastToken) {}
}
