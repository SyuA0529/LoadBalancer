package blog.syua.healthcheck;

import org.springframework.beans.factory.annotation.Value;

import lombok.Getter;

@Getter
public class HealthCheckResponse {

	@Value("${loadbalancer.healthcheck.success-ack}")
	public static final String SUCCESS_ACK = "hello";

	private String ack;

	public HealthCheckResponse() {
		ack = "";
	}

	public void setHealthy() {
		this.ack = SUCCESS_ACK;
	}

}
