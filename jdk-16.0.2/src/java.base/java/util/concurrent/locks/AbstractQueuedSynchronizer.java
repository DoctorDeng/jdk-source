/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;
import jdk.internal.misc.Unsafe;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h2>Usage</h2>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li>{@link #tryAcquire}
 * <li>{@link #tryRelease}
 * <li>{@link #tryAcquireShared}
 * <li>{@link #tryReleaseShared}
 * <li>{@link #isHeldExclusively}
 * </ul>
 *
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 *
 * (Shared mode is similar but may involve cascading signals.)
 *
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h2>Usage Examples</h2>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes some instrumentation methods:
 *
 * <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (!isHeldExclusively())
 *         throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Reports whether in locked state
 *     public boolean isLocked() {
 *       return getState() != 0;
 *     }
 *
 *     public boolean isHeldExclusively() {
 *       // a data race, but safe due to out-of-thin-air guarantees
 *       return getExclusiveOwnerThread() == Thread.currentThread();
 *     }
 *
 *     // Provides a Condition
 *     public Condition newCondition() {
 *       return new ConditionObject();
 *     }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()              { sync.acquire(1); }
 *   public boolean tryLock()        { return sync.tryAcquire(1); }
 *   public void unlock()            { sync.release(1); }
 *   public Condition newCondition() { return sync.newCondition(); }
 *   public boolean isLocked()       { return sync.isLocked(); }
 *   public boolean isHeldByCurrentThread() {
 *     return sync.isHeldExclusively();
 *   }
 *   public boolean hasQueuedThreads() {
 *     return sync.hasQueuedThreads();
 *   }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 * <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /*
     * Overview.
     *
     * The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers by
     * including explicit ("prev" and "next") links plus a "status"
     * field that allow nodes to signal successors when releasing
     * locks, and handle cancellation due to interrupts and timeouts.
     * The status field includes bits that track whether a thread
     * needs a signal (using LockSupport.unpark). Despite these
     * additions, we maintain most CLH locality properties.
     *
     * To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you set the head field, so the next eligible
     * waiter becomes first.
     *
     *  +------+  prev +-------+       +------+
     *  | head | <---- | first | <---- | tail |
     *  +------+       +-------+       +------+
     *
     * Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple point of demarcation
     * from unqueued to queued. The "next" link of the predecessor is
     * set by the enqueuing thread after successful CAS. Even though
     * non-atomic, this suffices to ensure that any blocked thread is
     * signalled by a predecessor when eligible (although in the case
     * of cancellation, possibly with the assistance of a signal in
     * method cleanQueue). Signalling is based in part on a
     * Dekker-like scheme in which the to-be waiting thread indicates
     * WAITING status, then retries acquiring, and then rechecks
     * status before blocking. The signaller atomically clears WAITING
     * status when unparking.
     *
     * Dequeuing on acquire involves detaching (nulling) a node's
     * "prev" node and then updating the "head". Other threads check
     * if a node is or was dequeued by checking "prev" rather than
     * head. We enforce the nulling then setting order by spin-waiting
     * if necessary. Because of this, the lock algorithm is not itself
     * strictly "lock-free" because an acquiring thread may need to
     * wait for a previous acquire to make progress. When used with
     * exclusive locks, such progress is required anyway. However
     * Shared mode may (uncommonly) require a spin-wait before
     * setting head field to ensure proper propagation. (Historical
     * note: This allows some simplifications and efficiencies
     * compared to previous versions of this class.)
     *
     * A node's predecessor can change due to cancellation while it is
     * waiting, until the node is first in queue, at which point it
     * cannot change. The acquire methods cope with this by rechecking
     * "prev" before waiting. The prev and next fields are modified
     * only via CAS by cancelled nodes in method cleanQueue. The
     * unsplice strategy is reminiscent of Michael-Scott queues in
     * that after a successful CAS to prev field, other threads help
     * fix next fields.  Because cancellation often occurs in bunches
     * that complicate decisions about necessary signals, each call to
     * cleanQueue traverses the queue until a clean sweep. Nodes that
     * become relinked as first are unconditionally unparked
     * (sometimes unnecessarily, but those cases are not worth
     * avoiding).
     *
     * A thread may try to acquire if it is first (frontmost) in the
     * queue, and sometimes before.  Being first does not guarantee
     * success; it only gives the right to contend. We balance
     * throughput, overhead, and fairness by allowing incoming threads
     * to "barge" and acquire the synchronizer while in the process of
     * enqueuing, in which case an awakened first thread may need to
     * rewait.  To counteract possible repeated unlucky rewaits, we
     * exponentially increase retries (up to 256) to acquire each time
     * a thread is unparked. Except in this case, AQS locks do not
     * spin; they instead interleave attempts to acquire with
     * bookkeeping steps. (Users who want spinlocks can use
     * tryAcquire.)
     *
     * To improve garbage collectibility, fields of nodes not yet on
     * list are null. (It is not rare to create and then throw away a
     * node without using it.) Fields of nodes coming off the list are
     * nulled out as soon as possible. This accentuates the challenge
     * of externally determining the first waiting thread (as in
     * method getFirstQueuedThread). This sometimes requires the
     * fallback of traversing backwards from the atomically updated
     * "tail" when fields appear null. (This is never needed in the
     * process of signalling though.)
     *
     * CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     *
     * Shared mode operations differ from Exclusive in that an acquire
     * signals the next waiter to try to acquire if it is also
     * Shared. The tryAcquireShared API allows users to indicate the
     * degree of propagation, but in most applications, it is more
     * efficient to ignore this, allowing the successor to try
     * acquiring in any case.
     *
     * Threads waiting on Conditions use nodes with an additional
     * link to maintain the (FIFO) list of conditions. Conditions only
     * need to link nodes in simple (non-concurrent) linked queues
     * because they are only accessed when exclusively held.  Upon
     * await, a node is inserted into a condition queue.  Upon signal,
     * the node is enqueued on the main queue.  A special status field
     * value is used to track and atomically trigger this.
     *
     * Accesses to fields head, tail, and state use full Volatile
     * mode, along with CAS. Node fields status, prev and next also do
     * so while threads may be signallable, but sometimes use weaker
     * modes otherwise. Accesses to field "waiter" (the thread to be
     * signalled) are always sandwiched between other atomic accesses
     * so are used in Plain mode. We use jdk.internal Unsafe versions
     * of atomic access methods rather than VarHandles to avoid
     * potential VM bootstrap issues.
     *
     * Most of the above is performed by primary internal method
     * acquire, that is invoked in some way by all exported acquire
     * methods.  (It is usually easy for compilers to optimize
     * call-site specializations when heavily used.)
     *
     * There are several arbitrary decisions about when and how to
     * check interrupts in both acquire and await before and/or after
     * blocking. The decisions are less arbitrary in implementation
     * updates because some users appear to rely on original behaviors
     * in ways that are racy and so (rarely) wrong in general but hard
     * to justify changing.
     *
     * Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     */

    // Node status bits, also used as argument and return values
    // 1. 节点状态 < 0: 此时节点对应线程获取锁成功、或已取消获取锁.
    // 2. 节点状态 = 0: 节点刚入队（CLH 队列）或者对应线程从休眠状态被唤醒（此时状态会被重置为 0）时状态都为 0, 该状态是一个瞬时态，最终会转换为 WAITING 或 COND.
    // 3. 节点状态 > 0: 此时线程正在等待获取锁，或者在条件变量上等待.
    static final int WAITING   = 1;          // must be 1           节点状态：等待
    static final int CANCELLED = 0x80000000; // must be negative    节点状态：已取消
    static final int COND      = 2;          // in a condition wait 节点状态：条件变量等待

    /** CLH Nodes */
    abstract static class Node {
        volatile Node prev;       // initially attached via casTail     通过 casTail() 方法添加的前驱节点.
        volatile Node next;       // visibly nonnull when signallable   后驱节点.
        Thread waiter;            // visibly nonnull when enqueued      节点对应等待线程.
        volatile int status;      // written by owner, atomic bit ops by others 节点状态.

        // methods for atomic operations
        final boolean casPrev(Node c, Node v) {  // for cleanQueue
            return U.weakCompareAndSetReference(this, PREV, c, v);
        }
        final boolean casNext(Node c, Node v) {  // for cleanQueue
            return U.weakCompareAndSetReference(this, NEXT, c, v);
        }
        final int getAndUnsetStatus(int v) {     // for signalling
            return U.getAndBitwiseAndInt(this, STATUS, ~v);
        }
        final void setPrevRelaxed(Node p) {      // for off-queue assignment
            U.putReference(this, PREV, p);
        }
        final void setStatusRelaxed(int s) {     // for off-queue assignment
            U.putInt(this, STATUS, s);
        }
        final void clearStatus() {               // for reducing unneeded signals
            U.putIntOpaque(this, STATUS, 0);
        }

        private static final long STATUS
            = U.objectFieldOffset(Node.class, "status");
        private static final long NEXT
            = U.objectFieldOffset(Node.class, "next");
        private static final long PREV
            = U.objectFieldOffset(Node.class, "prev");
    }

    // Concrete classes tagged by type
    static final class ExclusiveNode extends Node { }
    static final class SharedNode extends Node { }

    static final class ConditionNode extends Node
        implements ForkJoinPool.ManagedBlocker {
        // 下一个条件变量节点.
        ConditionNode nextWaiter;            // link to next waiting node

        /**
         * Allows Conditions to be used in ForkJoinPools without
         * risking fixed pool exhaustion. This is usable only for
         * untimed Condition waits, not timed versions.
         */
        public final boolean isReleasable() {
            return status <= 1 || Thread.currentThread().isInterrupted();
        }

        public final boolean block() {
            while (!isReleasable()) LockSupport.park();
            return true;
        }
    }

    /**
     * Head of the wait queue, lazily initialized.
     */
    // CLH 队列头节点, 为虚拟节点不存储实际数据.
    private transient volatile Node head;

    /**
     * Tail of the wait queue. After initialization, modified only via casTail.
     */
    private transient volatile Node tail;

    /**
     * The synchronization state.
     */
    // 同步状态
    private volatile int state;

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     *         value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        return U.compareAndSetInt(this, STATE, expect, update);
    }

    // Queuing utilities
    // 将 tail 节点设置为 v
    private boolean casTail(Node c, Node v) {
        return U.compareAndSetReference(this, TAIL, c, v);
    }

    /** tries once to CAS a new dummy node for head */
    private void tryInitializeHead() { // 尝试初始化头节点.
        Node h = new ExclusiveNode();
        if (U.compareAndSetReference(this, HEAD, null, h))
            tail = h;
    }

    /**
     * Enqueues the node unless null. (Currently used only for
     * ConditionNodes; other cases are interleaved with acquires.)
     */
    // 将节点入队，除非为空。（目前仅用于 ConditionNodes）
    final void enqueue(Node node) {
        if (node != null) {
            for (;;) {
                Node t = tail;
                node.setPrevRelaxed(t);        // avoid unnecessary fence
                if (t == null)                 // initialize
                    tryInitializeHead();
                else if (casTail(t, node)) {
                    t.next = node;
                    // 如果节点前驱节点状态小于 0 则直接唤醒节点对应线程.
                    if (t.status < 0)          // wake up to clean link
                        LockSupport.unpark(node.waiter);
                    break;
                }
            }
        }
    }

    /** Returns true if node is found in traversal from tail */
    final boolean isEnqueued(Node node) {
        for (Node t = tail; t != null; t = t.prev)
            if (t == node)
                return true;
        return false;
    }

    /**
     * Wakes up the successor of given node, if one exists, and unsets its
     * WAITING status to avoid park race. This may fail to wake up an
     * eligible thread when one or more have been cancelled, but
     * cancelAcquire ensures liveness.
     */
    // 唤醒给定节点的后继节点（如果存在），并取消其 WAITING 状态以避免 LockSupport.park 竞争。
    // 当一个或多个线程被取消时，可能无法唤醒符合条件的线程，但 cancelAcquire 可确保活跃度
    private static void signalNext(Node h) {
        Node s;
        if (h != null && (s = h.next) != null && s.status != 0) {
            // 将节点状态修改为 ~WAITING 即 -2
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }

    /** Wakes up the given node if in shared mode */
    private static void signalNextIfShared(Node h) {
        Node s;
        if (h != null && (s = h.next) != null &&
            (s instanceof SharedNode) && s.status != 0) {
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }

    /**
     * Main acquire method, invoked by all exported acquire methods.
     *
     * @param node null unless a reacquiring Condition 一般为 null 除非在 Condition 上等待
     * @param arg the acquire argument
     * @param shared true if shared mode else exclusive
     * @param interruptible if abort and return negative on interrupt
     * @param timed if true use timed waits
     * @param time if timed, the System.nanoTime value to timeout
     * @return positive if acquired, 0 if timed out, negative if interrupted
     */
    final int acquire(Node node, int arg, boolean shared,
                      boolean interruptible, boolean timed, long time) {
        Thread current = Thread.currentThread();
        // 线程的自旋或重试次数.
        byte spins = 0, postSpins = 0;   // retries upon unpark of first thread
        // interrupted：是否中断，first：传入的 node 是否是第一个节点
        boolean interrupted = false, first = false;
        Node pred = null;                // predecessor of node when enqueued

        /*
         * Repeatedly:
         *  Check if node now first
         *    if so, ensure head stable, else ensure valid predecessor
         *  if node is first or not yet enqueued, try acquiring
         *  else if node not yet created, create it
         *  else if not yet enqueued, try once to enqueue
         *  else if woken from park, retry (up to postSpins times)
         *  else if WAITING status not set, set and retry
         *  else park and clear WAITING status, and check cancellation
         */
        // 1. 如果节点不是 CLH 队列第一个节点，确保该节点存在有效的前驱节点
        // 2. 如果节点是第一个或尚未入队，则尝试 acquire
        // 3. 如果节点未创建则创建它
        // 4. 如果节点尚未入队，则尝试入队一次
        // 5. 如果从 LockSupport.park 唤醒，重试（最多 postSpins 次）
        // 6. 如果节点状态为初始状态（即 0）将其设置为 WAITING 状态
        // 7. 休眠当前线程. 如果线程被唤醒，清除等待状态（即状态设置为 0），并检查是否取消 acquire.
        for (;;) {
            // 1. 当节点不是 CLH 队列第一个节点时确保该节点存在有效的前驱节点.
            if (!first
                    && (pred = (node == null) ? null : node.prev) != null // 节点前驱不为 null(节点不为头节点)
                    && !(first = (head == pred))) {                       // 前驱不为头节点，即节点不为第一个节点
                // 1.1 如果前驱节点状态小于 0 （已取消或已成功 acquire）清理 CLH 队列中已被取消的节点，并跳过当前循环.
                if (pred.status < 0) {
                    cleanQueue();           // predecessor cancelled
                    continue;
                }
                // 1.2 节点前驱节点的前驱节点为头节点时，跳过当前循环.
                else if (pred.prev == null) {
                    // 主动告知运行时当前线程正在自旋进行忙等待，便于运行时优化自旋性能.
                    Thread.onSpinWait();    // ensure serialization
                    continue;
                }
            }
            // 2. 如果节点是第一个或尚未入队，则尝试 acquire.
            if (first || pred == null) {
                boolean acquired;
                try {
                    // 根据不同模式尝试 acquire.
                    if (shared)
                        acquired = (tryAcquireShared(arg) >= 0);
                    else
                        acquired = tryAcquire(arg);
                } catch (Throwable ex) {
                    // acquire 出现异常时，取消 acquire 的尝试.
                    cancelAcquire(node, interrupted, false);
                    throw ex;
                }
                if (acquired) {
                    // 如果 acquire 成功且是节点是第一个节点（即节点前驱为虚拟的头节点），则将当前节点置为头节点.
                    if (first) {
                        node.prev = null;
                        head = node;
                        pred.next = null;
                        node.waiter = null;
                        // 如果为共享锁模式，则唤醒下一个节点.
                        if (shared)
                            signalNextIfShared(node);
                        if (interrupted)
                            current.interrupt();
                    }
                    // acquire 成功后直接退出
                    return 1;
                }
            }
            // 3. 如果节点未创建则创建它（根据不同的模式创建对应类型节点）
            if (node == null) {                 // allocate; retry before enqueue
                if (shared)
                    node = new SharedNode();
                else
                    node = new ExclusiveNode();
            }
            // 4. 如果尚未入队，则尝试入队一次.
            else if (pred == null) {          // try to enqueue
                node.waiter = current;
                Node t = tail;
                node.setPrevRelaxed(t);         // avoid unnecessary fence
                // 尾节点为 null 时尝试初始化虚拟头节点，该节点不存储实际数据.
                if (t == null)
                    tryInitializeHead();
                // 尝试入队（将节点添加到队列尾部），入队失败重置节点的前驱节点.
                else if (!casTail(t, node))
                    node.setPrevRelaxed(null);  // back out
                // 将原尾节点的后驱指向当前入队的节点
                else
                    t.next = node;
            }
            // 5. 如果从 LockSupport.park 唤醒，重试（最多 postSpins 次）.
            else if (first && spins != 0) {
                --spins;                        // reduce unfairness on rewaits
                Thread.onSpinWait();
            }
            // 6. 如果节点状态为初始状态（刚入队或者因为被唤醒，状态被重置等）时将其状态修改为 WAITING（等待 acquire）.
            else if (node.status == 0) {
                node.status = WAITING;          // enable signal and recheck
            }
            // 7. 休眠当前线程。如果当线程被唤醒，清除状态（置为 0），并检查是否取消 acquire.
            else {
                long nanos;
                spins = postSpins = (byte)((postSpins << 1) | 1);
                // 如果不是超时 acquire，则将当前 acquire 的线程休眠.
                if (!timed)
                    LockSupport.park(this);
                // 如果超时 acquire，则将线程休眠指定时间.
                else if ((nanos = time - System.nanoTime()) > 0L)
                    LockSupport.parkNanos(this, nanos);
                else
                    // 如果 acquire 已超时则结束循环，跳转到 cancelAcquire() 取消线程 acquire 的尝试.
                    break;
                // 当等待 acquire 的线程被唤醒时:
                // 1. 清空线程状态: 置为 0.
                // 2. 当线程由于中断被唤醒时，如果需要报告中断信息，跳转到 cancelAcquire() 取消线程 acquire 的尝试，并报告中断状态.
                node.clearStatus();
                if ((interrupted |= Thread.interrupted()) && interruptible)
                    break;
            }
        }
        return cancelAcquire(node, interrupted, interruptible);
    }

    /**
     * Possibly repeatedly traverses from tail, unsplicing cancelled
     * nodes until none are found. Unparks nodes that may have been
     * relinked to be next eligible acquirer.
     */
    // 从尾部反复遍历，解开被取消的节点，直到找不到。 唤醒（unpark）可能已重新链接为下一个有资格 acquire 的节点
    private void cleanQueue() {
        for (;;) {                               // restart point
            // p <-- q <-- s (p 为 q 的前驱节点, q 为当前遍历的节点, s 为 q 的后驱节点)
            for (Node q = tail, s = null, p, n;;) { // (p, q, s) triples
                // 1.q 为 null 说明当前 CLH 队列还未初始化
                // 2.q 不为 null 但 q 的前驱 p 为 null。说明当前 q 是队列中的第一个节点，而等待队列的第一个节点是一个虚拟的头结点，不存储实际数据.
                // 1、2 满足一个说明当前队列为空, 无需再遍历队列, 直接结束
                if (q == null || (p = q.prev) == null)
                    return;                      // end of list
                // s == null && tail != q  即节点后驱为 null 但是当前节点却不为 tail 队尾节点，说明队列数据发生了变化（可能有新的节点插入）
                // s != null 且 s 前驱不为 q 或 s 状态小于 0 即已取消，也说明节点数据已发生变动
                // 当节点数据发生变动时需要结束当前循环重新从 CLH 队列尾部开始遍历队列.
                if (s == null ? tail != q : (s.prev != q || s.status < 0))
                    break;                       // inconsistent
                // 如果当前节点已取消(或 acquire 成功), 需要将节点从队列中移除.
                if (q.status < 0) {              // cancelled
                    // 将当前节点从队列中移除, 处理时根据节点是否为队尾（tail）节点做不同处理.
                    // 1. 当前节点为队尾节点时，直接将前驱变为队尾节点
                    // 2. 当前节点不为队尾节点将其前驱和后驱节点相连接
                    if ((s == null ? casTail(q, p) : s.casPrev(q, p)) &&
                        q.prev == p) {
                        p.casNext(q, s);         // OK if fails
                        // 前驱节点为 null 表示为头节点，此时唤醒下一个节点.
                        if (p.prev == null)
                            signalNext(p);
                    }
                    break;
                }
                // 上面的代码段在并发的情况下可能出现一种情况：
                // 线程 1 完成了 s.casPrev(q, p) 操作，现在 s 的 prev 指针指向了 p，但是此时 p 的 next 指针依然指向 q
                // 此时线程 1 被 cpu 挂起，线程 2 执行，当线程 2 执行到该处时，发现 p 的 next 指针还没有改变其指向，
                // 这种情况下，由于不确定线程 1 何时才会再次被调度，由发现此现象的线程帮助改变 p 的 next 指针，此即为下面这段代码的目的
                if ((n = p.next) != q) {         // help finish
                    if (n != null && q.prev == p) {
                        p.casNext(n, q);
                        if (p.prev == null)
                            signalNext(p);
                    }
                    break;
                }
                // 向前移动.
                s = q;
                q = q.prev;
            }
        }
    }

    /**
     * Cancels an ongoing attempt to acquire.
     *
     * @param node the node (may be null if cancelled before enqueuing)
     * @param interrupted true if thread interrupted
     * @param interruptible if should report interruption vs reset
     */
    // 取消指定节点对应线程 acquire.
    private int cancelAcquire(Node node, boolean interrupted,
                              boolean interruptible) {
        // 将节点从 CLH 队列中删除.
        if (node != null) {
            node.waiter = null;      // 绑定的线程置为 Null
            node.status = CANCELLED; // 状态修改为已取消
            if (node.prev != null)   // 如果节点不是头节点，清理队列中所有已取消的节点.
                cleanQueue();
        }
        // 如果线程由于中断而取消获取锁.
        if (interrupted) {
            // 报告中断.
            if (interruptible)
                return CANCELLED;
            // 重置线程中断状态.
            else
                Thread.currentThread().interrupt();
        }
        return 0;
    }

    // Main exported methods

    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     *         been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     *         state, so that any waiting threads may attempt to acquire;
     *         and {@code false} otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     *         mode succeeded but no subsequent shared-mode acquire can
     *         succeed; and a positive value if acquisition in shared
     *         mode succeeded and subsequent shared-mode acquires might
     *         also succeed, in which case a subsequent waiting thread
     *         must check availability. (Support for three different
     *         return values enables this method to be used in contexts
     *         where acquires only sometimes act exclusively.)  Upon
     *         success, this object has been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a {@link ConditionObject} method.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return {@code true} if synchronization is held exclusively;
     *         {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    // 如果与当前（调用）线程独占同步（即当前线程是独占模式下 acquire 成功的线程），则返回 true.
    // 每次调用 AbstractQueuedSynchronizer.ConditionObject 方法时都会调用此方法.
    // 此方法仅在 AbstractQueuedSynchronizer.ConditionObject 方法内部调用，因此如果不使用条件则无需定义
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     */
    // 以独占模式获取锁，忽略中断。 通过至少调用一次 tryAcquire 实现 ，在成功时返回。
    // 否则线程会排队，可能会反复阻塞和解除阻塞，调用 tryAcquire 直到成功。 此方法可用于实现方法 Lock.lock
    public final void acquire(int arg) {
        if (!tryAcquire(arg))
            acquire(null, arg, false, false, false, 0L);
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
        throws InterruptedException {
        if (Thread.interrupted() ||
            (!tryAcquire(arg) && acquire(null, arg, false, true, false, 0L) < 0))
            throw new InterruptedException();
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
        throws InterruptedException {
        if (!Thread.interrupted()) {
            if (tryAcquire(arg))
                return true;
            if (nanosTimeout <= 0L)
                return false;
            int stat = acquire(null, arg, false, true, true,
                               System.nanoTime() + nanosTimeout);
            if (stat > 0)
                return true;
            if (stat == 0)
                return false;
        }
        throw new InterruptedException();
    }

    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            signalNext(head);
            return true;
        }
        return false;
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            acquire(null, arg, true, false, false, 0L);
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
        throws InterruptedException {
        if (Thread.interrupted() ||
            (tryAcquireShared(arg) < 0 &&
             acquire(null, arg, true, true, false, 0L) < 0))
            throw new InterruptedException();
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (!Thread.interrupted()) {
            if (tryAcquireShared(arg) >= 0)
                return true;
            if (nanosTimeout <= 0L)
                return false;
            int stat = acquire(null, arg, true, true, true,
                               System.nanoTime() + nanosTimeout);
            if (stat > 0)
                return true;
            if (stat == 0)
                return false;
        }
        throw new InterruptedException();
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            signalNext(head);
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        for (Node p = tail, h = head; p != h && p != null; p = p.prev)
            if (p.status >= 0)
                return true;
        return false;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is, if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        Thread first = null, w; Node h, s;
        if ((h = head) != null && ((s = h.next) == null ||
                                   (first = s.waiter) == null ||
                                   s.prev == null)) {
            // traverse from tail on stale reads
            for (Node p = tail, q; p != null && (q = p.prev) != null; p = q)
                if ((w = p.waiter) != null)
                    first = w;
        }
        return first;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.waiter == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null && (s = h.next)  != null &&
            !(s instanceof SharedNode) && s.waiter != null;
    }

    /**
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     * <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread()
     *   && hasQueuedThreads()}</pre>
     *
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer.html#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     * <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     *         current thread, and {@code false} if the current thread
     *         is at the head of the queue or the queue is empty
     * @since 1.7
     */
    public final boolean hasQueuedPredecessors() {
        Thread first = null; Node h, s;
        if ((h = head) != null && ((s = h.next) == null ||
                                   (first = s.waiter) == null ||
                                   s.prev == null))
            first = getFirstQueuedThread(); // retry via getFirstQueuedThread
        return first != null && first != Thread.currentThread();
    }

    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.waiter != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.waiter;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!(p instanceof SharedNode)) {
                Thread t = p.waiter;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p instanceof SharedNode) {
                Thread t = p.waiter;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        return super.toString()
            + "[State = " + getState() + ", "
            + (hasQueuedThreads() ? "non" : "") + "empty queue]";
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link AbstractQueuedSynchronizer}
     * serving as the basis of a {@link Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /** First node of condition queue. */
        private transient ConditionNode firstWaiter;
        /** Last node of condition queue. */
        private transient ConditionNode lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() { }

        // Signalling methods

        /**
         * Removes and transfers one or all waiters to sync queue.
         */
        // 移除一个或所有等待的线程节点并将其转移到同步队列（即 CLH 队列）。
        private void doSignal(ConditionNode first, boolean all) {
            while (first != null) {
                ConditionNode next = first.nextWaiter;
                if ((firstWaiter = next) == null)
                    lastWaiter = null;
                if ((first.getAndUnsetStatus(COND) & COND) != 0) {
                    enqueue(first);
                    if (!all)
                        break;
                }
                first = next;
            }
        }

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signal() {
            ConditionNode first = firstWaiter;
            // 如果线程没有获取锁直接抛出异常.
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            // 当存在在条件变量上进行等待的线程时才进行通知.
            if (first != null)
                doSignal(first, false);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            ConditionNode first = firstWaiter;
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            if (first != null)
                doSignal(first, true);
        }

        // Waiting methods

        /**
         * Adds node to condition list and releases lock.
         *
         * @param node the node
         * @return savedState to reacquire after wait
         */
        private int enableWait(ConditionNode node) {
            // 如果当前线程 acquire 成功（即获取锁成功）才能在条件变量上等待, 否则抛出 IllegalMonitorStateException 异常.
            if (isHeldExclusively()) {
                node.waiter = Thread.currentThread();
                // 状态设置为 3.
                node.setStatusRelaxed(COND | WAITING);
                // 将条件变量节点添加到条件变量等待队列尾部.
                ConditionNode last = lastWaiter;
                if (last == null)
                    firstWaiter = node;
                else
                    last.nextWaiter = node;
                lastWaiter = node;
                // 获取同步状态，并释放当前线程持有的锁，如果释放失败程序继续往下走会抛出 IllegalMonitorStateException 异常.
                int savedState = getState();
                if (release(savedState))
                    return savedState;
            }
            // 当前线程未 acquire 成功时不允许在条件变量上等待, 此时直接抛出异常.
            node.status = CANCELLED; // lock not held or inconsistent
            throw new IllegalMonitorStateException();
        }

        /**
         * Returns true if a node that was initially placed on a condition
         * queue is now ready to reacquire on sync queue.
         * @param node the node
         * @return true if is reacquiring
         */
        // 判断指定条件变量节点是否准备好重新在同步队列（CLH 队列）中 acquire.
        // 当节点在 CLH 队列中时返回 true.
        private boolean canReacquire(ConditionNode node) {
            // check links, not status to avoid enqueue race
            // 检查链接，而不是状态以避免排队竞争
            return node != null && node.prev != null && isEnqueued(node);
        }

        /**
         * Unlinks the given node and other non-waiting nodes from
         * condition queue unless already unlinked.
         */
        // 移除条件变量等待队列中节点状态为：0（初始状态）、WAITING、CANCELLED 的节点.
        private void unlinkCancelledWaiters(ConditionNode node) {
            if (node == null || node.nextWaiter != null || node == lastWaiter) {
                ConditionNode w = firstWaiter, trail = null;
                while (w != null) {
                    ConditionNode next = w.nextWaiter;
                    if ((w.status & COND) == 0) {
                        w.nextWaiter = null;
                        if (trail == null)
                            firstWaiter = next;
                        else
                            trail.nextWaiter = next;
                        if (next == null)
                            lastWaiter = trail;
                    } else
                        trail = w;
                    w = next;
                }
            }
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            ConditionNode node = new ConditionNode();
            int savedState = enableWait(node);
            LockSupport.setCurrentBlocker(this); // for back-compatibility
            boolean interrupted = false;
            while (!canReacquire(node)) {
                if (Thread.interrupted())
                    interrupted = true;
                else if ((node.status & COND) != 0) {
                    try {
                        ForkJoinPool.managedBlock(node);
                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                } else
                    Thread.onSpinWait();    // awoke while enqueuing
            }
            LockSupport.setCurrentBlocker(null);
            node.clearStatus();
            acquire(node, savedState, false, false, false, 0L);
            if (interrupted)
                Thread.currentThread().interrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled or interrupted.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            ConditionNode node = new ConditionNode();
            // 让持有锁的当前线程释放锁并将其添加到条件变量等待队列中，同时还将节点状态设置为 3.
            int savedState = enableWait(node);
            LockSupport.setCurrentBlocker(this); // for back-compatibility
            boolean interrupted = false, cancelled = false;

            // 当节点未准备好重新获取锁时（即节点不在 CLH 队列中）：
            // 1. 如果线程被中断且在依然条件变量上等待直接退出该循环.
            // 2. 如果线程未中断且状态不为 0、WAITING 或 CANCELLED 运行 ForkJoinPool 中给定的阻塞任务.
            // 3. 否则自旋等待.
            while (!canReacquire(node)) {
                if (interrupted |= Thread.interrupted()) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break;              // else interrupted after signal
                } else if ((node.status & COND) != 0) {
                    try {
                        ForkJoinPool.managedBlock(node);
                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                } else
                    Thread.onSpinWait();    // awoke while enqueuing
            }
            // 当节点处于 CLH 队列中时（线程被中断、或通过 signal、signalAll 被重新添加 CLH 队列中) 尝试 acquire.
            LockSupport.setCurrentBlocker(null);
            // 将节点状态重置为 0 然后尝试 acquire.
            node.clearStatus();
            acquire(node, savedState, false, false, false, 0L);
            if (interrupted) {
                // 如果线程在条件变量上等待时被中断，从条件变量队列中取消当前节点和其他非等待节点的链接.
                if (cancelled) {
                    unlinkCancelledWaiters(node);
                    throw new InterruptedException();
                }
                // 如果线程中断时不在条件变量上等待，则主动中断当前线程.
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled, interrupted, or timed out.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            ConditionNode node = new ConditionNode();
            int savedState = enableWait(node);
            long nanos = (nanosTimeout < 0L) ? 0L : nanosTimeout;
            long deadline = System.nanoTime() + nanos;
            boolean cancelled = false, interrupted = false;
            while (!canReacquire(node)) {
                if ((interrupted |= Thread.interrupted()) ||
                    (nanos = deadline - System.nanoTime()) <= 0L) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break;
                } else
                    LockSupport.parkNanos(this, nanos);
            }
            node.clearStatus();
            acquire(node, savedState, false, false, false, 0L);
            if (cancelled) {
                unlinkCancelledWaiters(node);
                if (interrupted)
                    throw new InterruptedException();
            } else if (interrupted)
                Thread.currentThread().interrupt();
            long remaining = deadline - System.nanoTime(); // avoid overflow
            return (remaining <= nanosTimeout) ? remaining : Long.MIN_VALUE;
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled, interrupted, or timed out.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * <li>If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            ConditionNode node = new ConditionNode();
            int savedState = enableWait(node);
            boolean cancelled = false, interrupted = false;
            while (!canReacquire(node)) {
                if ((interrupted |= Thread.interrupted()) ||
                    System.currentTimeMillis() >= abstime) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break;
                } else
                    LockSupport.parkUntil(this, abstime);
            }
            node.clearStatus();
            acquire(node, savedState, false, false, false, 0L);
            if (cancelled) {
                unlinkCancelledWaiters(node);
                if (interrupted)
                    throw new InterruptedException();
            } else if (interrupted)
                Thread.currentThread().interrupt();
            return !cancelled;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled, interrupted, or timed out.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * <li>If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            ConditionNode node = new ConditionNode();
            int savedState = enableWait(node);
            long nanos = (nanosTimeout < 0L) ? 0L : nanosTimeout;
            long deadline = System.nanoTime() + nanos;
            boolean cancelled = false, interrupted = false;
            while (!canReacquire(node)) {
                if ((interrupted |= Thread.interrupted()) ||
                    (nanos = deadline - System.nanoTime()) <= 0L) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break;
                } else
                    LockSupport.parkNanos(this, nanos);
            }
            node.clearStatus();
            acquire(node, savedState, false, false, false, 0L);
            if (cancelled) {
                unlinkCancelledWaiters(node);
                if (interrupted)
                    throw new InterruptedException();
            } else if (interrupted)
                Thread.currentThread().interrupt();
            return !cancelled;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if ((w.status & COND) != 0)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if ((w.status & COND) != 0)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<>();
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if ((w.status & COND) != 0) {
                    Thread t = w.waiter;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    // Unsafe
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long STATE
        = U.objectFieldOffset(AbstractQueuedSynchronizer.class, "state");
    private static final long HEAD
        = U.objectFieldOffset(AbstractQueuedSynchronizer.class, "head");
    private static final long TAIL
        = U.objectFieldOffset(AbstractQueuedSynchronizer.class, "tail");

    static {
        Class<?> ensureLoaded = LockSupport.class;
    }
}
