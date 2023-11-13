package blog.syua.utils;

import java.io.Closeable;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadPoolUtils {

	private ThreadPoolUtils() {
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void removeThreadPool(ExecutorService threadPool, Closeable closeable) {
		log.info("ThreadPoolUtils: Start Remove ThreadPool - {}", closeable);
		threadPool.shutdown();
		new Thread(() -> {
			try {
				threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
				closeable.close();
				log.info("ThreadPoolUtils: Success Remove ThreadPool - {}", closeable);
			} catch (Exception exception) {
				threadPool.shutdownNow();
				log.error("ThreadPoolUtils: Error occur in Unregister Node\n{}",
					Arrays.toString(exception.getStackTrace()));
				Thread.currentThread().interrupt();
			}
			log.info("ThreadPoolUtils: Finish Remove ThreadPool - {}", closeable);
		}).start();
	}

}
