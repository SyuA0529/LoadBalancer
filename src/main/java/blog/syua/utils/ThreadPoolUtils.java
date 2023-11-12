package blog.syua.utils;

import java.io.Closeable;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadPoolUtils {

	private static final Logger logger = LoggerFactory.getLogger(ThreadPoolUtils.class);

	private ThreadPoolUtils() {
	}

	public static void removeThreadPool(ExecutorService threadPool, Closeable closeable) {
		logger.info("ThreadPoolUtils: removeThreadPool");
		threadPool.shutdown();
		new Thread(() -> {
			try {
				threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
				closeable.close();
			} catch (Exception exception) {
				threadPool.shutdownNow();
				logger.error("UdpNodeManager: Error occur in unregisterNode\n{}",
					Arrays.toString(exception.getStackTrace()));
				Thread.currentThread().interrupt();
			}
		}).start();
	}

}
