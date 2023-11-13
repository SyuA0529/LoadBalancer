package blog.syua.healthcheck;

import lombok.Getter;

@Getter
public class HealthCheckRequest {

	@Getter
	private static final HealthCheckRequest instance = new HealthCheckRequest();

	private final String cmd = "hello";

}
