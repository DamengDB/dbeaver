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

import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.sql.semantics.SQLQueryRecognitionContext;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SQLQuerySemanticSolver {

    private static final Log log = Log.getLog(SQLQuerySemanticSolver.class);

    @FunctionalInterface
    public interface SemanticProducer0<T> {
        T produce(SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducer1<A, T> {
        T produce(A arg, SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducer2<A1, A2, T> {
        T produce(A1 arg1, A2 arg2, SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducer3<A1, A2, A3, T> {
        T produce(A1 arg1, A2 arg2, A3 arg3, SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducerN<A, T> {
        T produce(A[] args, SQLQueryRecognitionContext ctx);
    }

    public interface FutureSemantic0 extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer0<T> producer);
    }

    public interface FutureSemantic1<A> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer1<A, T> producer);
    }

    public interface FutureSemantic2<A1, A2> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer2<A1, A2, T> producer);
    }

    public interface FutureSemantic3<A1, A2, A3> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer3<A1, A2, A3, T> producer);
    }

    public interface FutureSemanticN<A> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducerN<A, T> producer);
    }

    public abstract class DepsNode<T> implements SQLQuerySemanticEdge<T> {
        private final DoubleLinkedList.Item<DepsNode<?>> listNode;

        private final Set<DepsNode<?>> consumers = new HashSet<>();
        private final Set<DepsNode<?>> sources;
        private final Set<DepsNode<?>> sourcesPrepared;

        private volatile T value;
        private volatile boolean isPrepared = false;

        private DepsNode(SQLQuerySemanticEdge<?> ... sourcesEdges) {
            this.listNode = new DoubleLinkedList.Item<>(this);
            this.sources = new HashSet<>(sourcesEdges.length);
            this.sourcesPrepared = new HashSet<>(sourcesEdges.length);
            synchronized (this) { // TODO consider lock order
                for (SQLQuerySemanticEdge<?> sourceEdge : sourcesEdges) {
                    DepsNode<?> source = sourceEdge.source();
                    if (this.sources.add(source)) {
                        source.consumers.add(this);
                        if (source.isPrepared) {
                            this.sourcesPrepared.add(source);
                        }
                    } else {
                        throw new IllegalArgumentException("Duplicated dependency");
                    }
                }
                this.enqueueIfReady();
            }
            SQLQuerySemanticSolver.this.awaitingItems.addLast(this.listNode);
        }

        private void doWork() {
            this.value = this.doWorkImpl();
            this.isPrepared = true;

            for (DepsNode<?> consumer : this.consumers) {
                consumer.promoteSource(this);
            }
        }

        private void promoteSource(DepsNode<?> source) {
            if (!this.sources.contains(source)) {
                throw new IllegalStateException(); // should never happen
            }

            if (!this.sourcesPrepared.add(this)) {
                throw new IllegalStateException(); // should never happen
            }

            this.enqueueIfReady();
        }

        private void enqueueIfReady() {
            if (this.sourcesPrepared.size() >= this.sources.size()) {
                SQLQuerySemanticSolver.this.awaitingItems.remove(this.listNode);
                SQLQuerySemanticSolver.this.queuedItems.add(this);
            }
        }

        protected abstract T doWorkImpl();

        @Override
        public DepsNode<T> source() {
            return this;
        }

        private T value() {
            if (this.isPrepared) {
                return this.value;
            } else {
                throw new IllegalStateException(); // should never happen
            }
        }
    }

    private final LinkedBlockingQueue<DepsNode<?>> queuedItems = new LinkedBlockingQueue<>();

    private final DoubleLinkedList<DepsNode<?>> awaitingItems = new DoubleLinkedList<>();

    private final SQLQueryRecognitionContext context;

    private final CompletableFuture<Boolean> isSolved = new CompletableFuture<>();

    private final AtomicBoolean isJoining = new AtomicBoolean(false);

    private final Thread solvingThread;

    public SQLQuerySemanticSolver(SQLQueryRecognitionContext context) {
        this.context = context;
        this.solvingThread = new Thread(this::doSolve, "Semantic solver");
        this.solvingThread.setDaemon(true);
    }

    public <T> SQLQuerySemanticEdge<T> prepared(T value) {
        return this.prepare(s -> value);
    }

    public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer0<T> producer) {
        DepsNode<T> item = new DepsNode<>() {
            @Override
            protected T doWorkImpl() {
                return producer.produce(SQLQuerySemanticSolver.this.context);
            }
        };
        this.queuedItems.add(item);
        return item;
    }

    public <A> FutureSemantic1<A> forAll(SQLQuerySemanticEdge<A> a) {
        return new FutureSemantic1<A>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer1<A, T> producer) {
                return new DepsNode<T>(a) {
                    @Override
                    protected T doWorkImpl() {
                        return producer.produce(a.source().value(), SQLQuerySemanticSolver.this.context);
                    }
                };
            }
        };
    }

    public <A1, A2> FutureSemantic2<A1, A2> forAll(SQLQuerySemanticEdge<A1> a1, SQLQuerySemanticEdge<A2> a2) {
        return new FutureSemantic2<A1, A2>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer2<A1, A2, T> producer) {
                return new DepsNode<T>(a1, a2) {
                    @Override
                    protected T doWorkImpl() {
                        return producer.produce(a1.source().value(), a2.source().value(), SQLQuerySemanticSolver.this.context);
                    }
                };
            }
        };
    }

    public <A1, A2, A3> FutureSemantic3<A1, A2, A3> forAll(SQLQuerySemanticEdge<A1> a1, SQLQuerySemanticEdge<A2> a2, SQLQuerySemanticEdge<A3> a3) {
        return new FutureSemantic3<A1, A2, A3>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer3<A1, A2, A3, T> producer) {
                return new DepsNode<T>(a1, a2, a3) {
                    @Override
                    protected T doWorkImpl() {
                        return producer.produce(a1.source().value(), a2.source().value(), a3.source().value(), SQLQuerySemanticSolver.this.context);
                    }
                };
            }
        };
    }

    public <A> FutureSemanticN<A> forAll(SQLQuerySemanticEdge<A> ... args) {
        return new FutureSemanticN<A>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducerN<A, T> producer) {
                return new DepsNode<T>(args) {
                    @Override
                    protected T doWorkImpl() {
                        @SuppressWarnings("unchecked")
                        A[] values = (A[]) new Object[args.length];
                        for (int i = 0; i < args.length; i++) {
                            values[i] = args[i].source().value();
                        }
                        return producer.produce(values, SQLQuerySemanticSolver.this.context);
                    }
                };
            }
        };
    }

    public void start() {
        this.solvingThread.start();
    }

    public boolean join() {
        this.isJoining.set(true);
        try {
            return this.isSolved.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
            return false;
        }
    }

    private void doSolve() {
        try {
            while (!this.context.getMonitor().isCanceled() && (
                (!this.isJoining.get() && !this.awaitingItems.isEmpty()) ||
                (this.isJoining.get() && !this.queuedItems.isEmpty())
            )) {
                DepsNode<?> node = this.queuedItems.poll(100, TimeUnit.MILLISECONDS);
                if (node != null) {
                    node.doWork();
                }
            }
            this.isSolved.complete(true);
        } catch (RuntimeException | InterruptedException e) {
            log.error(e);
            this.isSolved.complete(false);
        }
    }
}
