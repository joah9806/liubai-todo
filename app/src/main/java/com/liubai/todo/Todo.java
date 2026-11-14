package com.liubai.todo;

final class Todo {
    final long id;
    String text;
    boolean done;
    final long createdAt;
    long completedAt;

    Todo(long id, String text, boolean done, long createdAt, long completedAt) {
        this.id = id;
        this.text = text;
        this.done = done;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }
}
