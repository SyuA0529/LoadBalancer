package blog.syua.node.groupmanager;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import blog.syua.node.group.ForwardInfo;
import blog.syua.node.group.NodeGroup;
import blog.syua.node.node.Protocol;
import blog.syua.node.groupmanager.NodeGroupManager;
import blog.syua.node.groupmanager.NodeGroupManagerImpl;
import blog.syua.node.group.TcpNodeGroup;
import blog.syua.node.group.UdpNodeGroup;
import blog.syua.node.node.TcpNode;
import blog.syua.node.node.UdpNode;

@SuppressWarnings("unchecked")
@DisplayName("NodeGroup Manager 테스트")
class NodeGroupManagerImplTest {

	private NodeGroupManager nodeGroupManager;
	private SoftAssertions softAssertions;

	@BeforeEach
	void beforeEach() {
		nodeGroupManager = new NodeGroupManagerImpl();
		softAssertions = new SoftAssertions();
	}

	@Nested
	@DisplayName("Method: registerNode")
	class MethodRegisterNode {
		@Test
		@DisplayName("새로운 유형의 포워딩 정보일 경우 새로 노드를 생성하고, 새로 생성한 노드 그룹에 이를 저장한다")
		void RegisterNodeToNewNodeGroup() throws IOException, ReflectiveOperationException {
			//given
			TcpNode tcpNode = new TcpNode(InetAddress.getLocalHost(), 0);
			UdpNode udpNode = new UdpNode(InetAddress.getLocalHost(), 0);

			//when
			nodeGroupManager.registerNode(Protocol.TCP, tcpNode.getIpAddr(), tcpNode.getPort());
			nodeGroupManager.registerNode(Protocol.UDP, udpNode.getIpAddr(), udpNode.getPort());

			//then
			ConcurrentHashMap<ForwardInfo, NodeGroup> nodeGroups =
				(ConcurrentHashMap<ForwardInfo, NodeGroup>)getFieldObject(nodeGroupManager.getClass(), "nodeGroups",
					nodeGroupManager);

			softAssertions.assertThat(nodeGroups.values().size()).isEqualTo(2);
			softAssertions.assertThat(nodeGroups.get(ForwardInfo.of(tcpNode))).isInstanceOf(TcpNodeGroup.class);
			softAssertions.assertThat(nodeGroups.get(ForwardInfo.of(udpNode))).isInstanceOf(UdpNodeGroup.class);
			softAssertions.assertAll();
		}

		@Test
		@DisplayName("기존 유형의 포워딩 정보인 경우 새로 노드를 생성하고, 해당하는 기존 노드 그룹에 이를 저장한다")
		void registerNodeToExistingNodeGroup() throws IOException, ReflectiveOperationException {
			//given
			nodeGroupManager.registerNode(Protocol.TCP, InetAddress.getLocalHost(), 0);
			nodeGroupManager.registerNode(Protocol.UDP, InetAddress.getLocalHost(), 0);

			TcpNode tcpNode = new TcpNode(InetAddress.getLocalHost(), 0);
			UdpNode udpNode = new UdpNode(InetAddress.getLocalHost(), 1);

			//when
			nodeGroupManager.registerNode(tcpNode.getProtocol(), tcpNode.getIpAddr(), tcpNode.getPort());
			nodeGroupManager.registerNode(udpNode.getProtocol(), udpNode.getIpAddr(), udpNode.getPort());

			//then
			ConcurrentHashMap<ForwardInfo, NodeGroup> nodeGroups = (ConcurrentHashMap<ForwardInfo, NodeGroup>)
				getFieldObject(nodeGroupManager.getClass(), "nodeGroups", nodeGroupManager);
			softAssertions.assertThat(nodeGroups.size()).isEqualTo(3);
			softAssertions.assertThat(nodeGroups.get(ForwardInfo.of(udpNode)))
				.isInstanceOf(UdpNodeGroup.class);

			NodeGroup tcpNodeGroup = nodeGroups.get(ForwardInfo.of(tcpNode));
			softAssertions.assertThat(tcpNodeGroup).isInstanceOf(TcpNodeGroup.class);

			Queue<TcpNode> tcpNodes = (Queue<TcpNode>)getFieldObject(tcpNodeGroup.getClass(), "tcpNodes", tcpNodeGroup);
			softAssertions.assertThat(tcpNodes.size()).isEqualTo(2);
			softAssertions.assertThat(tcpNodes.contains(tcpNode)).isTrue();
			softAssertions.assertAll();
		}
	}

	@Nested
	@DisplayName("Method: unRegisterNode")
	class MethodUnRegisterNode {
		@Test
		@DisplayName("존재하는 노드인 경우 노드 그룹에서 이를 삭제한다")
		void removeNodeFromNodeGroup() throws IOException, ReflectiveOperationException {
			//given
			TcpNode tcpNode = new TcpNode(InetAddress.getLocalHost(), 0);
			UdpNode udpNode = new UdpNode(InetAddress.getLocalHost(), 0);
			nodeGroupManager.registerNode(Protocol.TCP, tcpNode.getIpAddr(), tcpNode.getPort());
			nodeGroupManager.registerNode(Protocol.UDP, udpNode.getIpAddr(), udpNode.getPort());

			//when
			nodeGroupManager.unRegisterNode(tcpNode.getProtocol(), tcpNode.getIpAddr(), tcpNode.getPort());
			nodeGroupManager.unRegisterNode(udpNode.getProtocol(), udpNode.getIpAddr(), udpNode.getPort());

			//then
			ConcurrentHashMap<ForwardInfo, NodeGroup> nodeGroups = (ConcurrentHashMap<ForwardInfo, NodeGroup>)
				getFieldObject(nodeGroupManager.getClass(), "nodeGroups", nodeGroupManager);
			assertThat(nodeGroups.values()).isEmpty();
		}

		@Test
		@DisplayName("존재하지 않는 노드인 경우 IllegalArgumentException 예외를 발생시킨다")
		void throwIllegalStateException() {
			//given
			//when
			//then
			Assertions.assertThatThrownBy(() ->
					nodeGroupManager.unRegisterNode(Protocol.TCP, InetAddress.getLocalHost(), 3))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("존재하지 않는 노드입니다");
		}
	}

	private Object getFieldObject(Class<?> clazz, String fieldName, Object object) throws
		ReflectiveOperationException {
		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(object);
	}

}