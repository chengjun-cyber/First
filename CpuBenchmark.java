
public class CpuBenchmark {
    private static final int OPERATIONS_PER_THREAD = 100_000_000;

    public static void main(String[] args) throws InterruptedException {
        testCpuPerformance(1, "单核");
        testCpuPerformance(Runtime.getRuntime().availableProcessors(), "多核");
    }

    private static void testCpuPerformance(int threadCount, String label) throws InterruptedException {
        System.out.printf("CPU %s性能测试，线程数: %d\n", label, threadCount);
        Thread[] threads = new Thread[threadCount];
        long start = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                double result = 0;
                for (long j = 0; j < OPERATIONS_PER_THREAD; j++) {
                    result += Math.sin(j) * Math.cos(j);
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();

        long end = System.currentTimeMillis();
        long totalOps = (long) threadCount * OPERATIONS_PER_THREAD;
        double seconds = (end - start) / 1000.0;
        System.out.printf("总耗时: %.2f 秒（即程序完成所有计算所用的时间）\n", seconds);
        System.out.printf("总操作数: %d（所有线程累计完成的计算次数）\n", totalOps);
        System.out.printf("每秒操作数: %.2f MOPS（每秒可完成的百万次操作，体现%s运算能力）\n\n", totalOps / seconds / 1_000_000, label);
    }
}
