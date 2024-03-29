# jdk-source

个人 JDK 源码分析项目，用于阅读 JDK 源码时添加注释和调试使用。目前个人已阅读的源码如下：

| 分组  | 名称                                                         | 说明                                        |
| ----- | ------------------------------------------------------------ | ------------------------------------------- |
| j.u.c | [AbstractQueuedSynchronizer](jdk-16.0.2/src/java.base/java/util/concurrent/locks/AbstractQueuedSynchronizer.java) | j.u.c 中许多同步器的基础框架                |
|       | [ReentrantReadWriteLock](jdk-16.0.2/src/java.base/java/util/concurrent/locks/ReentrantReadWriteLock.java) | 可重入的读写锁实现，基于 AQS 实现           |
|       | [ReentrantLock](jdk-16.0.2/src/java.base/java/util/concurrent/locks/ReentrantLock.java) | 可重入锁实现                                |
|       | [ConcurrentHashMap](jdk-16.0.2/src/java.base/java/util/concurrent/ConcurrentHashMap.java) | 并发安全的散列表实现                        |
|       | [CountDownLatch](jdk-16.0.2/src/java.base/java/util/concurrent/locks/CountDownLatch.java) | 基于 AQS 实现的锁存器（latch）用于线程同步  |
| 其他  | [ThreadLocal](jdk-16.0.2/src/java.base/java/lang/ThreadLocal.java) | 通过 ThreadLocal 可以实现 __线程封闭__ 机制 |
|       | [HashMap](jdk-16.0.2/src/java.base/java/util/HashMap.java)   | 散列表实现                                  |

