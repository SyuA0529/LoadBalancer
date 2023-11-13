package blog.syua.healthcheck;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import blog.syua.node.groupmanager.NodeGroupManager;
import blog.syua.node.node.Node;
import blog.syua.node.node.TcpNode;
import blog.syua.node.node.UdpNode;

@SuppressWarnings("unchecked")
@DisplayName("HealthChecker 테스트")
class HealthCheckerImplTest {

	private HealthChecker healthChecker;
	private SoftAssertions softAssertions;

	private int tcpHealthCheckCount;
	private int udpHealthCheckCount;

	@BeforeEach
	void beforeEach() {
		NodeGroupManager nodeGroupManager = mock(NodeGroupManager.class);
		healthChecker = new HealthCheckerImpl(nodeGroupManager);
		softAssertions = new SoftAssertions();
		tcpHealthCheckCount = 0;
		udpHealthCheckCount = 0;
	}

	@Nested
	@DisplayName("Method: onRegisterNode")
	class MethodOnRegisterNode {

		@Test
		@DisplayName("일정 주기마다 노드를 헬스체크 작업을 수행한다")
		void startHealthCheck() throws IOException, InterruptedException {
			//given

			TcpNode mockedTcpNode = getMockedTcpNode(() -> {
				tcpHealthCheckCount += 1;
				return true;
			});
			UdpNode mockedUdpNode = getMockedUdpNode(() -> {
				udpHealthCheckCount += 1;
				return true;
			});

			//when
			healthChecker.onRegisterNode(mockedTcpNode);
			healthChecker.onRegisterNode(mockedUdpNode);
			Thread.sleep(7000);

			//then
			softAssertions.assertThat(tcpHealthCheckCount).isGreaterThanOrEqualTo(2);
			softAssertions.assertThat(udpHealthCheckCount).isGreaterThanOrEqualTo(2);
			softAssertions.assertAll();
		}

		@Test
		@DisplayName("헬스 체크에 실패한 경우 헬스체크 작업을 중단하고 NodeGroupManager에게 노드 제거 요청을 한다")
		void stopHealthCHeckAndRequestUnRegisterNodeToNodeGroupManager() throws
			IOException,
			InterruptedException,
			ReflectiveOperationException {
			//given
			TcpNode mockedTcpNode = getMockedTcpNode(() -> {
				tcpHealthCheckCount += 1;
				return tcpHealthCheckCount < 2;
			});
			UdpNode mockedUdpNode = getMockedUdpNode(() -> {
				udpHealthCheckCount += 1;
				return udpHealthCheckCount < 2;
			});

			//when
			healthChecker.onRegisterNode(mockedTcpNode);
			healthChecker.onRegisterNode(mockedUdpNode);
			Thread.sleep(10000);

			//then
			softAssertions.assertThat(tcpHealthCheckCount).isLessThanOrEqualTo(3);
			softAssertions.assertThat(udpHealthCheckCount).isLessThanOrEqualTo(3);

			softAssertions.assertThat(getScheduledFutures()).isEmpty();
			softAssertions.assertAll();
		}

		@Test
		@DisplayName("이미 등록된 노드를 재등록하면 IllegalArgumentException 예외를 발생시킨다")
		void throwIllegalArgumentException() throws IOException {
			//given
			TcpNode mockedTcpNode = getMockedTcpNode(() -> {
				tcpHealthCheckCount += 1;
				return tcpHealthCheckCount < 2;
			});
			healthChecker.onRegisterNode(mockedTcpNode);

			//when
			//then
			assertThatThrownBy(() -> healthChecker.onRegisterNode(mockedTcpNode))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미 존재하는 노드입니다");
		}
	}

	@Nested
	@DisplayName("Method: onUnRegisterNode")
	class MethodOnUnRegisterNode {
		@Test
		@DisplayName("전달받은 노드의 헬스체크 작업을 중단한다")
		void stopHealthCheck() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
			//given
			TcpNode mockedTcpNode = getMockedTcpNode(() -> {
				tcpHealthCheckCount += 1;
				return tcpHealthCheckCount < 2;
			});
			UdpNode mockedUdpNode = getMockedUdpNode(() -> {
				udpHealthCheckCount += 1;
				return udpHealthCheckCount < 2;
			});
			healthChecker.onRegisterNode(mockedTcpNode);
			healthChecker.onRegisterNode(mockedUdpNode);

			//when
			Thread.sleep(4000);
			healthChecker.onUnRegisterNode(mockedTcpNode);
			healthChecker.onUnRegisterNode(mockedUdpNode);

			//then
			softAssertions.assertThat(tcpHealthCheckCount).isEqualTo(1);
			softAssertions.assertThat(tcpHealthCheckCount).isEqualTo(1);
			softAssertions.assertThat(getScheduledFutures()).isEmpty();
			softAssertions.assertAll();
		}
	}

	private ConcurrentHashMap<Node, ScheduledFuture<?>> getScheduledFutures() throws
		NoSuchFieldException,
		IllegalAccessException {
		Field field = healthChecker.getClass()
			.getDeclaredField("scheduledFutures");
		field.setAccessible(true);
		return (ConcurrentHashMap<Node, ScheduledFuture<?>>)field.get(healthChecker);
	}

	private static UdpNode getMockedUdpNode(Supplier<Boolean> supplier) throws IOException {
		return new UdpNode(InetAddress.getLocalHost().getHostAddress(), 0) {
			@Override
			public boolean isHealthy() {
				return supplier.get();
			}
		};
	}

	private static TcpNode getMockedTcpNode(Supplier<Boolean> supplier) throws IOException {
		return new TcpNode(InetAddress.getLocalHost().getHostAddress(), 0) {
			@Override
			public boolean isHealthy() {
				return supplier.get();
			}
		};
	}

}