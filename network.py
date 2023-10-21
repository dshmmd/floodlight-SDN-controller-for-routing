from mininet.net import Mininet
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import setLogLevel, info
from mininet.node import RemoteController


def run():
    info("*** Initialize net\n")
    net = Mininet(controller=RemoteController, link=TCLink)

    info("*** Adding controller\n")
    net.addController('c0', controller=RemoteController, ip='192.168.0.6', port=6653)

    info("*** Adding hosts\n")
    hosts = []
    for i in range(8):
        hosts.append(net.addHost(f'h{i + 1}'))

    info("*** Adding switches\n")
    switches = []
    for i in range(8):
        dpid = '{:0>16}'.format(i + 1)
        switches.append(str(net.addSwitch(f's{i + 1}', dpid=dpid, protocol="OpenFlow13")))

    info("*** Creating links to hosts\n")
    for i in range(8):
        net.addLink(switches[i], hosts[i], bw=1000, delay='1ms')
    for i, j in [(0, 2), (0, 7), (2, 5), (2, 7), (2, 3), (3, 4), (3, 1),
                 (3, 6), (7, 5), (7, 6), (5, 4), (4, 1), (4, 6), (1, 6)]:
        net.addLink(switches[i], switches[j], bw=1000, delay='1ms')

    info("*** Starting network\n")
    net.start()

    info("*** Running CLI\n")
    CLI(net)

    info("*** Stopping network\n")
    net.stop()


if __name__ == '__main__':
    setLogLevel('info')
    run()
