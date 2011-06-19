/*******************************************************************************
 *Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************/
/*
 * Author: chris grzegorczyk <grze@eucalyptus.com>
 */
package com.eucalyptus.cluster;

import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import javax.persistence.PersistenceException;
import org.apache.log4j.Logger;
import com.eucalyptus.address.Address;
import com.eucalyptus.address.Addresses;
import com.eucalyptus.auth.Accounts;
import com.eucalyptus.auth.principal.AccountFullName;
import com.eucalyptus.auth.principal.UserFullName;
import com.eucalyptus.cluster.callback.UnassignAddressCallback;
import com.eucalyptus.component.ServiceConfigurations;
import com.eucalyptus.config.ClusterConfiguration;
import com.eucalyptus.records.EventRecord;
import com.eucalyptus.records.EventType;
import com.eucalyptus.util.LogUtil;
import com.eucalyptus.util.NotEnoughResourcesAvailable;
import com.eucalyptus.util.async.AsyncRequests;
import com.google.common.collect.Sets;
import edu.ucsb.eucalyptus.cloud.Network;
import edu.ucsb.eucalyptus.cloud.NetworkToken;
import edu.ucsb.eucalyptus.cloud.ResourceToken;
import edu.ucsb.eucalyptus.msgs.ClusterAddressInfo;

public class ClusterState {
  private static Logger                           LOG                   = Logger.getLogger( ClusterState.class );
  private String                                  clusterName;
  private static NavigableSet<Integer>            availableVlans        = populate( );
  private Integer                                 mode                  = 1;
  private Integer                                 addressCapacity;
  private Boolean                                 publicAddressing      = false;
  private Boolean                                 addressingInitialized = false;
  private ConcurrentNavigableMap<ClusterAddressInfo, Integer> orphans               = new ConcurrentSkipListMap<ClusterAddressInfo, Integer>( );
  
  public void clearOrphan( ClusterAddressInfo address ) {
    Integer delay = orphans.remove( address );
    delay = ( delay == null ? 0 : delay );
    if ( delay > 2 ) {
      LOG.warn( "Forgetting stale orphan address mapping from cluster " + clusterName + " for " + address.toString( ) );
    }
  }
  public void handleOrphan( ClusterAddressInfo address ) {
    Integer orphanCount = 1;
    orphanCount = orphans.putIfAbsent( address, orphanCount );
    EventRecord.caller( ClusterState.class, EventType.ADDRESS_STATE, "Found orphaned public ip address: " + LogUtil.dumpObject( address ) + " count=" + orphanCount ).debug( );
    orphanCount = ( orphanCount == null ) ? 1 : orphanCount;
    orphans.put( address, orphanCount + 1 );
    EventRecord.caller( ClusterState.class, EventType.ADDRESS_STATE, "Updated orphaned public ip address: " + LogUtil.dumpObject( address ) + " count=" + orphanCount ).debug( );
    if ( orphanCount > 3 ) {
      EventRecord.caller( ClusterState.class, EventType.ADDRESS_STATE, "Unassigning orphaned public ip address: " + LogUtil.dumpObject( address ) + " count=" + orphanCount ).warn( );
      try {
        final Address addr = Addresses.getInstance( ).lookup( address.getAddress( ) );
        if( addr.isAssigned( ) ) {
          AsyncRequests.newRequest( new UnassignAddressCallback( address ) ).dispatch( this.clusterName );
        } else if ( addr.isSystemOwned( ) ) {
          addr.release( );
        }
      } catch ( NoSuchElementException e ) {
      }
      orphans.remove( address );
    }
  }
  
  public Boolean hasPublicAddressing( ) {
    return this.publicAddressing;
  }
  
  public Boolean isAddressingInitialized( ) {
    return this.addressingInitialized;
  }
  
  public void setAddressingInitialized( Boolean addressingInitialized ) {
    this.addressingInitialized = addressingInitialized;
  }
  
  public void setPublicAddressing( Boolean publicAddressing ) {
    this.publicAddressing = publicAddressing;
  }
  
  public static void trim( ) {
    NavigableSet<Integer> newVlanList = Sets.newTreeSet( );
    int min = 2;
    int max = 4095;
    try {
      for ( ClusterConfiguration cc : ServiceConfigurations.getConfigurations( ClusterConfiguration.class ) ) {
        if ( cc.getMinVlan( ) != null ) min = cc.getMinVlan( ) > min ? cc.getMinVlan( ) : min;
        if ( cc.getMaxVlan( ) != null ) max = cc.getMaxVlan( ) < max ? cc.getMaxVlan( ) : max;
      }
    } catch ( PersistenceException e ) {
      LOG.debug( e, e );
    }
    for ( int i = min; i < max; i++ )
      newVlanList.add( i );
    newVlanList.removeAll( availableVlans );
    availableVlans.removeAll( availableVlans.headSet( min ) );
    availableVlans.removeAll( availableVlans.tailSet( max ) );
    for ( int i = min; i < max; i++ ) {
      if ( !newVlanList.contains( i ) ) {
        availableVlans.add( i );
      }
    }
    EventRecord.here( ClusterState.class, EventType.CONFIG_VLANS, Integer.toString( min ), Integer.toString( max ),
                                 availableVlans.toString( )
                                               .substring( 0, 50 > availableVlans.toString( ).length( ) ? availableVlans.toString( ).length( ) : 50 ) ).debug( );
  }
  
  private static NavigableSet<Integer> populate( ) {
    NavigableSet<Integer> list = new ConcurrentSkipListSet<Integer>( );
    for ( int i = 2; i < 4095; i++ )
      list.add( i );
    return list;
  }
  
  public ClusterState( String clusterName ) {
    this.clusterName = clusterName;
  }
  
  public NetworkToken extantAllocation( String accountId, String networkName, String networkUuid, int vlan ) throws NetworkAlreadyExistsException {
    AccountFullName accountFn = Accounts.lookupAccountFullNameById( accountId );
    NetworkToken netToken = new NetworkToken( this.clusterName, accountFn, networkName, networkUuid, vlan );
    if ( !ClusterState.availableVlans.remove( vlan ) ) {
      throw new NetworkAlreadyExistsException( );
    }
    return netToken;
  }
  
  public static NetworkToken getNetworkAllocation( UserFullName userFullName, ResourceToken rscToken, String networkName ) throws NotEnoughResourcesAvailable {
    ClusterState.trim( );
    try {
      Network network = getVlanAssignedNetwork( networkName );      
      NetworkToken token = network.createNetworkToken( rscToken.getCluster( ) );
      EventRecord.caller( NetworkToken.class, EventType.TOKEN_RESERVED, token.toString( ) ).info( );
      return token;
    } catch ( NoSuchElementException e ) {
      LOG.debug( e, e );
      throw new NotEnoughResourcesAvailable( "Failed to create registry entry for network named: " + networkName );
    }
  }
  private static Network getVlanAssignedNetwork( String networkName ) throws NotEnoughResourcesAvailable {
    Network network = Networks.getInstance( ).lookup( networkName );
    Integer vlan = network.getVlan( );
    if ( vlan == null || Integer.valueOf( 0 ).equals( vlan ) ) {
      vlan = ClusterState.availableVlans.pollFirst( );
      if ( vlan == null ) {
        throw new NotEnoughResourcesAvailable( "Not enough resources available: vlan tags" );
      } else if ( !network.initVlan( vlan ) ) {
        ClusterState.availableVlans.add( vlan );
        throw new NotEnoughResourcesAvailable( "Not enough resources available: an error occured obtaining a usable vlan tag" );
      } else {
        EventRecord.caller( NetworkToken.class, EventType.TOKEN_RESERVED, network.toString( ) ).info( );
      }
    }
    return network;
  }
  
  public void releaseNetworkAllocation( NetworkToken token ) {
    EventRecord.caller( NetworkToken.class, EventType.TOKEN_RETURNED, token.toString( ) ).info( );
    try {
      Network existingNet = Networks.getInstance( ).lookup( token.getName( ) );
      if ( !existingNet.hasTokens( ) ) {
        ClusterState.availableVlans.add( existingNet.getVlan( ) );
      }
      Networks.getInstance( ).remove( token.getName( ) );
    } catch ( NoSuchElementException e ) {}
  }
  
  @Override
  public boolean equals( final Object o ) {
    if ( this == o ) return true;
    if ( o == null || getClass( ) != o.getClass( ) ) return false;
    
    ClusterState cluster = ( ClusterState ) o;
    
    if ( !this.getClusterName( ).equals( cluster.getClusterName( ) ) ) return false;
    
    return true;
  }
  
  @Override
  public int hashCode( ) {
    return this.getClusterName( ).hashCode( );
  }
  
  public String getClusterName( ) {
    return clusterName;
  }
  
  public Integer getMode( ) {
    return mode;
  }
  
  public void setMode( Integer mode ) {
    this.mode = mode;
  }
  
  public Integer getAddressCapacity( ) {
    return addressCapacity;
  }
  
  public void setAddressCapacity( Integer addressCapacity ) {
    this.addressCapacity = addressCapacity;
  }
  
  @Override
  public String toString( ) {
    return String.format( "ClusterState [addressCapacity=%s, clusterName=%s, mode=%s]", this.addressCapacity, this.clusterName, this.mode );
  }
  
}
