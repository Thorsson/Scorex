package scorex.core.network.peer

import java.net.{InetSocketAddress, NetworkInterface}

import scorex.core.utils.NetworkTime

import scala.collection.JavaConverters._
import scala.collection.mutable


//todo: persistence
class PeerDatabaseImpl(bindAddress: InetSocketAddress,
                       declaredAddress: Option[InetSocketAddress],
                       filename: Option[String]) extends PeerDatabase {

  private val whitelistPersistence = mutable.Map[InetSocketAddress, PeerInfo]()

  private val blacklist = mutable.Map[String, NetworkTime.Time]()

  override def addOrUpdateKnownPeer(address: InetSocketAddress, peerInfo: PeerInfo): Unit = {
    val updatedPeerInfo = whitelistPersistence.get(address).fold(peerInfo) { dbPeerInfo =>
      val nodeNameOpt = peerInfo.nodeName orElse dbPeerInfo.nodeName
      val connTypeOpt = peerInfo.connectionType orElse  dbPeerInfo.connectionType
      PeerInfo(peerInfo.lastSeen, nodeNameOpt, connTypeOpt)
    }
    whitelistPersistence.put(address, updatedPeerInfo)
  }

  override def blacklistPeer(address: InetSocketAddress, time: NetworkTime.Time): Unit = {
    whitelistPersistence.remove(address)
    if (!isBlacklisted(address)) blacklist += address.getHostName -> time
  }

  override def isBlacklisted(address: InetSocketAddress): Boolean = {
    blacklist.synchronized(blacklist.contains(address.getHostName))
  }

  override def knownPeers(excludeSelf: Boolean): Map[InetSocketAddress, PeerInfo] = {
    if (excludeSelf) {
      val localAddresses = if (bindAddress.getAddress.isAnyLocalAddress) {
        NetworkInterface.getNetworkInterfaces.asScala
          .flatMap(_.getInetAddresses.asScala
            .map(a => new InetSocketAddress(a, bindAddress.getPort)))
          .toSet
      } else Set(bindAddress)

      val excludedAddresses = localAddresses ++ declaredAddress.toSet
      knownPeers(false).filterKeys(!excludedAddresses(_))
    } else {
      whitelistPersistence.keys.flatMap(k => whitelistPersistence.get(k).map(v => k -> v)).toMap
    }

  }

  override def blacklistedPeers(): Seq[String] = blacklist.keys.toSeq

  override def isEmpty(): Boolean = whitelistPersistence.isEmpty
}
