import javax.net.ssl.*;
        import java.io.*;
        import java.net.*;
        import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
        import java.util.*;
        import java.util.concurrent.atomic.AtomicLong;

public class test {
    private static final int THREAD_COUNT = 32; // 可根据CPU和带宽调整
    private static final long DOWNLOAD_SIZE_MB = 400;
    private static final long DOWNLOAD_SIZE_BYTES = DOWNLOAD_SIZE_MB * 1024 * 1024;
    private static final String TEST_URL = "https://speed.cloudflare.com/__down?bytes=" + DOWNLOAD_SIZE_BYTES;

    public static void main(String[] args) {
        try {
            // 信任所有证书
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.out.println("SSL配置失败: " + e.getMessage());
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            List<Long> speedSamples = Collections.synchronizedList(new ArrayList<>());
            long totalBytes = downloadFile(TEST_URL, speedSamples);
            long endTime = System.currentTimeMillis();

            double seconds = (endTime - startTime) / 1000.0;
            double avgSpeed = (totalBytes / 1024.0 / 1024.0) / seconds;

            System.out.printf("下载总量: %.2f MB\n", totalBytes / 1024.0 / 1024.0);
            System.out.printf("耗时: %.2f 秒\n", seconds);
            System.out.printf("平均网速: %.2f MB/s\n", avgSpeed);

            // 输出每秒速度波动
            System.out.println("每秒速度波动（MB/s）:");
            for (int i = 0; i < speedSamples.size(); i++) {
                System.out.printf("第%d秒: %.2f MB/s\n", i + 1, speedSamples.get(i) / 1024.0 / 1024.0);
            }
        } catch (Exception e) {
            System.out.println("下载失败: " + e.getMessage());
        }
    }

    private static long downloadFile(String fileUrl, List<Long> speedSamples) throws Exception {
        long fileSize = DOWNLOAD_SIZE_BYTES; // 直接使用已知下载总量

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        Future<Long>[] futures = new Future[THREAD_COUNT];
        long partSize = fileSize / THREAD_COUNT;
        long totalBytes = 0;

        AtomicLong globalBytes = new AtomicLong(0);
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> {
            speedSamples.add(globalBytes.getAndSet(0));
        }, 1, 1, TimeUnit.SECONDS);

        for (int i = 0; i < THREAD_COUNT; i++) {
            long start = i * partSize;
            long end = (i == THREAD_COUNT - 1) ? fileSize - 1 : (start + partSize - 1);
            futures[i] = executor.submit(new DownloadTask(fileUrl, start, end, globalBytes));
        }

        for (Future<Long> future : futures) {
            try {
                totalBytes += future.get();
            } catch (Exception e) {
                System.out.println("线程下载失败: " + e.getMessage());
            }
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        sampler.shutdownNow();
        return totalBytes;
    }

    static class DownloadTask implements Callable<Long> {
        private final String fileUrl;
        private final long start;
        private final long end;
        private final AtomicLong globalBytes;

        public DownloadTask(String fileUrl, long start, long end, AtomicLong globalBytes) {
            this.fileUrl = fileUrl;
            this.start = start;
            this.end = end;
            this.globalBytes = globalBytes;
        }

        @Override
        public Long call() {
            long totalBytes = 0;
            HttpURLConnection conn = null;
            try {
                URL url = URI.create(fileUrl).toURL();
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Range", "bytes=" + start + "-" + end);

                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    long partLength = end - start + 1;
                    long lastPercent = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        totalBytes += bytesRead;
                        globalBytes.addAndGet(bytesRead);
                        long percent = (totalBytes * 100) / partLength;
                        if (percent - lastPercent >= 10 || percent == 100) {
                            System.out.printf("线程 %s 下载进度: %d%%\n", Thread.currentThread().getName(), percent);
                            lastPercent = percent;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.printf("线程 %s 异常: %s\n", Thread.currentThread().getName(), e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
            return totalBytes;
        }
    }
}