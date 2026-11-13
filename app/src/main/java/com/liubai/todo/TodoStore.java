package com.liubai.todo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TodoStore {
    private static final String PREFS = "todo_store";
    private static final String KEY = "todos_v1";

    private final SharedPreferences preferences;

    TodoStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Todo> load() {
        String raw = preferences.getString(KEY, null);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }

        List<Todo> active = new ArrayList<>();
        List<Todo> completed = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String text = object.optString("text", "").trim();
                if (text.isEmpty()) {
                    continue;
                }
                boolean done = object.optBoolean("done", false);
                long now = System.currentTimeMillis();
                long createdAt = object.optLong("createdAt", now);
                long completedAt = object.optLong("completedAt", done ? createdAt : 0L);
                Todo todo = new Todo(
                        object.optLong("id", createdAt),
                        text,
                        done,
                        createdAt,
                        completedAt);
                if (done) {
                    completed.add(todo);
                } else {
                    active.add(todo);
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        // Keep unfinished items on top. Completed items stay at the bottom,
        // oldest completion first so a newly completed item is appended at the end.
        Collections.sort(completed, (left, right) -> Long.compare(left.completedAt, right.completedAt));
        active.addAll(completed);
        return active;
    }

    void save(List<Todo> todos) {
        JSONArray array = new JSONArray();
        for (Todo todo : todos) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", todo.id);
                object.put("text", todo.text);
                object.put("done", todo.done);
                object.put("createdAt", todo.createdAt);
                object.put("completedAt", todo.completedAt);
                array.put(object);
            } catch (Exception ignored) {
                // A single malformed item should not discard the rest.
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }
}
