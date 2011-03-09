package com.eucalyptus.auth.entities;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PersistenceContext;
import javax.persistence.Table;
import javax.persistence.Transient;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import com.eucalyptus.entities.AbstractPersistent;

/**
 * Database account entity.
 * 
 * @author wenye
 *
 */

@Entity
@PersistenceContext( name = "eucalyptus_auth" )
@Table( name = "auth_account" )
@Cache( usage = CacheConcurrencyStrategy.TRANSACTIONAL )
public class AccountEntity extends AbstractPersistent implements Serializable {

  @Transient
  private static final long serialVersionUID = 1L;

  // Account name, it is unique.
  @Column( name = "auth_account_name", unique = true )
  String name;
  
  public AccountEntity( ) {
  }
  
  public AccountEntity( String name ) {
    this( );
    this.name = name;
  }

  public static AccountEntity newInstanceWithId( final String id ) {
    AccountEntity a = new AccountEntity( );
    a.setId( id );
    return a;
  }

  @Override
  public boolean equals( final Object o ) {
    if ( this == o ) return true;
    if ( o == null || getClass( ) != o.getClass( ) ) return false;
    
    AccountEntity that = ( AccountEntity ) o;    
    if ( !name.equals( that.name ) ) return false;
    
    return true;
  }

  @Override
  public String toString( ) {
    StringBuilder sb = new StringBuilder( );
    sb.append( "Account[" );
    sb.append( "ID=" ).append( this.getId( ) ).append( ", " );
    sb.append( "name=" ).append( this.getName( ) );
    sb.append( "]" );
    return sb.toString( );
  }
  
  public String getName( ) {
    return this.name;
  }

  public void setName( String name ) {
    this.name = name;
  }

}