/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.model.sql.semantics.solver;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class DoubleLinkedList<T> implements Iterable<T> {

    public static class Item<T> {
        public final T value;

        private DoubleLinkedList<T> list = null;
        private Item<T> next = null;
        private Item<T> prev = null;

        public Item(T value) {
            this.value = value;
        }
    }

    private Item<T> head = null;
    private Item<T> tail = null;

    private int count = 0;

    public DoubleLinkedList() {
    }

    public int size() {
        return this.count;
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    public Item<T> addLast(T value) {
        return this.addLast(new Item<>(value));
    }

    public Item<T> addLast(Item<T> newItem) {
        return this.addItemAfter(this.tail, newItem);
    }

    public Item<T> addFirst(T value) {
        return this.addFirst(new Item<>(value));
    }

    public Item<T> addFirst(Item<T> newItem) {
        return this.addItemAfter(null, newItem);
    }

    public Item<T> addAfter(Item<T> item, Item<T> newItem) {
        return this.addItemAfter(item, newItem);
    }

    public Item<T> addBefore(Item<T> item, Item<T> newItem) {
        return this.addItemAfter(item.prev, newItem);
    }

    public T first() {
        return this.head == null ? null : this.head.value;
    }

    public T last() {
        return this.tail == null ? null : this.tail.value;
    }

    public Item<T> head() {
        return this.head;
    }

    public Item<T> tail() {
        return this.tail;
    }

    public T removeFirst() {
        if (this.isEmpty()) {
            throw new IllegalStateException();
        } else {
            return this.remove(this.head);
        }
    }

    public T removeLast() {
        if (this.isEmpty()) {
            throw new IllegalStateException();
        } else {
            return this.remove(this.tail);
        }
    }

    private Item<T> addItemAfter(Item<T> prevItem, Item<T> newItem) {
        if (newItem == null || newItem.list != null) {
            throw new IllegalArgumentException();
        }

        if (this.count == 0) {
            if (prevItem != null) {
                throw new IllegalArgumentException();
            } else {
                newItem.next = null;
                newItem.prev = null;
                this.head = newItem;
                this.tail = newItem;
            }
        } else {
            if (prevItem == null) {
                newItem.next = this.head;
                newItem.prev = null;
                this.head.prev = newItem;
                this.head = newItem;
            } else if (prevItem.list != this) {
                throw new IllegalArgumentException();
            } else if (prevItem == this.tail) {
                newItem.next = null;
                newItem.prev = this.tail;
                this.tail.next = newItem;
                this.tail = newItem;
            } else {
                newItem.next = prevItem.next;
                newItem.prev = prevItem;
                prevItem.next.prev = newItem;
                prevItem.next = newItem;
            }
        }

        newItem.list = this;
        this.count++;

        return newItem;
    }

    public T remove(Item<T> item) {
        if (item == null || item.list != this) {
            throw new IllegalArgumentException();
        }

        if (this.count == 1) {
            this.head = null;
            this.tail = null;
        } else {
            if (this.head == item) {
                this.head = item.next;
                this.head.prev = null;
            } else if (this.tail == item) {
                this.tail = item.prev;
                this.tail.next = null;
            } else {
                item.prev.next = item.next;
                item.next.prev = item.prev;
            }
        }

        item.prev = null;
        item.next = null;
        item.list = null;
        this.count--;

        return item.value;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Item<T> nextItem = DoubleLinkedList.this.head;

            @Override
            public boolean hasNext() {
                return this.nextItem != null;
            }

            @Override
            public T next() {
                Item<T> nextItem = this.nextItem;
                if (nextItem == null) {
                    throw new NoSuchElementException();
                } else {
                    T result = nextItem.value;
                    this.nextItem = nextItem.next;
                    return result;
                }
            }
        };
    }

}
