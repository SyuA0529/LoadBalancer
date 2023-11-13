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
		log.info("ThreadPoolUtils: removeThreadPool");
		threadPool.shutdown();
		new Thread(() -> {
			try {
				threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
				closeable.close();
			} catch (Exception exception) {
				threadPool.shutdownNow();
				log.error("UdpNodeManager: Error occur in unregisterNode\n{}",
					Arrays.toString(exception.getStackTrace()));
				Thread.currentThread().interrupt();
			}
		}).start();
	}

}
