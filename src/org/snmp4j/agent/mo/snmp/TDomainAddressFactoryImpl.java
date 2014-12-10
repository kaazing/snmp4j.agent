/*_############################################################################
  _## 
  _##  SNMP4J-Agent - TDomainAddressFactoryImpl.java  
  _## 
  _##  Copyright (C) 2005-2009  Frank Fock (SNMP4J.org)
  _##  
  _##  Licensed under the Apache License, Version 2.0 (the "License");
  _##  you may not use this file except in compliance with the License.
  _##  You may obtain a copy of the License at
  _##  
  _##      http://www.apache.org/licenses/LICENSE-2.0
  _##  
  _##  Unless required by applicable law or agreed to in writing, software
  _##  distributed under the License is distributed on an "AS IS" BASIS,
  _##  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  _##  See the License for the specific language governing permissions and
  _##  limitations under the License.
  _##  
  _##########################################################################*/

package org.snmp4j.agent.mo.snmp;

import org.snmp4j.smi.OID;
import java.net.Inet4Address;
import org.snmp4j.smi.UdpAddress;
import java.net.Inet6Address;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.log.LogFactory;
import org.snmp4j.log.LogAdapter;

public class TDomainAddressFactoryImpl implements TDomainAddressFactory {

  private static final LogAdapter logger =
      LogFactory.getLogger(TDomainAddressFactoryImpl.class);

  public TDomainAddressFactoryImpl() {
  }

  public Address createAddress(OID transportDomain, OctetString address) {
    if (TransportDomains.snmpUDPDomain.equals(transportDomain) ||
        TransportDomains.transportDomainUdpIpv4.equals(transportDomain) ||
        TransportDomains.transportDomainTcpIpv4.equals(transportDomain) ||
        TransportDomains.transportDomainUdpIpv6.equals(transportDomain) ||
        TransportDomains.transportDomainTcpIpv6.equals(transportDomain)) {
      TransportIpAddress transportIpAddress;
      if (TransportDomains.transportDomainTcpIpv4.equals(transportDomain) ||
          TransportDomains.transportDomainTcpIpv6.equals(transportDomain)) {
        transportIpAddress = new TcpAddress();
      }
      else {
        transportIpAddress = new UdpAddress();
      }
      try {
        transportIpAddress.setTransportAddress(address);
      }
      catch (Exception ex) {
        logger.debug("Invalid TransportAddress format '" + address +
                     "' for domain " + transportDomain);
        return null;
      }
      return transportIpAddress;
    }
    return null;
  }

  public boolean isValidAddress(OID transportDomain, OctetString address) {
    try {
      Address addr = createAddress(transportDomain, address);
      if (addr != null) {
        return true;
      }
    }
    catch (Exception ex) {
      logger.debug("Address is not valid TDomain address: " + address+
                   "; details: "+ex.getMessage());
    }
    return false;
  }

  public OID getTransportDomain(Address address) {
    if (address instanceof TransportIpAddress) {
      TransportIpAddress tipaddr = (TransportIpAddress) address;
      if (tipaddr.getInetAddress() instanceof Inet4Address) {
        if (address instanceof UdpAddress) {
          return TransportDomains.transportDomainUdpIpv4;
        }
        else if (address instanceof TcpAddress) {
          return TransportDomains.transportDomainTcpIpv4;
        }
      }
      else if (tipaddr.getInetAddress() instanceof Inet6Address) {
        if (address instanceof UdpAddress) {
          return TransportDomains.transportDomainUdpIpv6;
        }
        else if (address instanceof TcpAddress) {
          return TransportDomains.transportDomainTcpIpv6;
        }
      }
    }
    return null;
  }

  public OctetString getAddress(Address address) {
    if (address instanceof TransportIpAddress) {
      TransportIpAddress tipaddr = (TransportIpAddress) address;
      byte[] addrBytes = tipaddr.getInetAddress().getAddress();
      OctetString addr = new OctetString(addrBytes);
      addr.append((byte) (tipaddr.getPort() >> 8));
      addr.append((byte) (tipaddr.getPort() & 0xFF));
      return addr;
    }
    return null;
  }

}
