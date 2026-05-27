## 更新说明

- 支持 `corePoolSize` / `maximumPoolSize`
- 支持有界任务队列和拒绝策略
- 支持非核心线程空闲超时退出
- 支持统计当前worker数量和活跃线程数量
- 支持 `shutdown()`、`shutdownNow()` 和 `awaitTermination(timeout)`
- worker 执行任务时会捕获异常，避免任务异常导致线程退出

修改后的实现覆盖了线程池创建、任务提交、队列缓存、扩容、拒绝、关闭和超时回收等核心流程。

## 压测结果

压测配置：

- `corePoolSize = 2`
- `maximumPoolSize = 4`
- `queueCapacity = 100`
- `totalTasks = 1000`
- 每个任务模拟执行 `100ms`

运行结果：

```text
提交任务数: 1000
提交后 worker 数量: 4
提交后活跃线程数: 4
拒绝任务数: 896
实际开始执行任务数: 104
实际完成任务数: 104
最终 worker 数量: 0
线程池是否正常结束: true