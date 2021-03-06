package com.wowodc12.blogdemo.rest.controllers;

import java.text.SimpleDateFormat;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORedirect;
import com.webobjects.appserver.WORequest;
import com.webobjects.eocontrol.EOClassDescription;
import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation.NSTimestampFormatter;
import com.wowodc12.blogdemo.enums.SyncInfoStatus;
import com.wowodc12.blogdemo.model.BlogEntry;
import com.wowodc12.blogdemo.model.DelegatePKHistory;
import com.wowodc12.blogdemo.model.SyncInfo;

import er.extensions.appserver.ERXHttpStatusCodes;
import er.extensions.eof.ERXDatabaseContextDelegate;
import er.extensions.eof.ERXKeyFilter;
import er.rest.ERXRestContext;
import er.rest.ERXRestFetchSpecification;
import er.rest.IERXRestDelegate;
import er.rest.routes.ERXRouteResults;
import er.rest.routes.ERXRouteUrlUtils;

public class BlogEntriesController extends BaseRestController {

  // Wed, 15 Nov 1995 04:58:08 GMT
  protected static SimpleDateFormat isoDateFormatter = new SimpleDateFormat("E, d MMM y H:mm:s zz");

  public BlogEntriesController(WORequest request) {
    super(request);
  }

  @Override
  public WOActionResults createAction() throws Throwable {
    checkSecurity();
    BlogEntry newEntry = create(inFilter());
    editingContext().saveChanges();
    return response(newEntry, outFilter());
  }
  
  @Override
  public WOActionResults showAction() throws Throwable {
    String uniqueTitle = routeObjectForKey("uniqueTitle");
    BlogEntry entry = null;

    // ------ Check if the client already have a copy of the content ------
    String ifModifiedSinceHeader = request().headerForKey("If-Modified-Since");
    String ifNoneMatchHeader = request().headerForKey("If-None-Match");
    
    SyncInfo syncInfo = SyncInfo.fetchSyncInfo(editingContext(), SyncInfo.DELEGATED_PRIMARY_KEY_VALUE.eq(uniqueTitle));
    if (syncInfo != null) {
      // ----- If your etag value is the same as the client, that means the content is the same
      if (syncInfo.etag().equals(ifNoneMatchHeader)) {
        return response(ERXHttpStatusCodes.NOT_MODIFIED);
      }

      // ----- If your last modified date is the same as the client, that means the content is the same
      if (syncInfo.etag().equals(ifModifiedSinceHeader)) {
        java.util.Date ifModifiedDate = isoDateFormatter.parse(ifModifiedSinceHeader);
        if ((ifModifiedDate.before(syncInfo.lastModified())) || (ifModifiedDate.equals(syncInfo.lastModified()))) {
          return response(ERXHttpStatusCodes.NOT_MODIFIED);
        }
      }
    }

    try {
      // ------ Do we have a blog entry for this title? ------
      entry = blogEntryForTitle(uniqueTitle);
    } catch (ERXDatabaseContextDelegate.ObjectNotAvailableException ex) {
      
      // ------ Do we have a trace of that title? ------
      DelegatePKHistory history = DelegatePKHistory.fetchDelegatePKHistory(editingContext(), DelegatePKHistory.DELEGATED_PRIMARY_KEY_VALUE.eq(uniqueTitle));
      if (history != null) {
        syncInfo = history.syncInfo();
        
        // ----- Can we link this history to a current title? ------
        if (syncInfo != null) {
          
          // ----- Was marked as deleted, so send a 410 Gone ------
          if (syncInfo.state().equals(SyncInfoStatus.DELETED)) {
            return response(ERXHttpStatusCodes.GONE);
          }
          
          // ----- We have the content, so let's redirect the client to the new URL ------
          entry = blogEntryForTitle(syncInfo.delegatedPrimaryKeyValue());
          String urlForRedirect = ERXRouteUrlUtils.actionUrlForRecord(this.context(), entry, "show", this.format().name(), null, false, false);
          WORedirect redirect = new WORedirect(this.context());
          redirect.setUrl(urlForRedirect);
          return redirect;
          
        } else {
          // ------ No sync details, send a 410 Gone
          return response(ERXHttpStatusCodes.GONE);
        }
        
      } else {
        // ----- We didn't find a history, so let's send a 404 Not Found
        return response(ERXHttpStatusCodes.NOT_FOUND);
      }
    }
    
    // ------ If we have sync info for that object, send the Etag and Last-Modified headers ------
    ERXRouteResults response = (ERXRouteResults)response(entry, outFilter());
    if (syncInfo != null) {
      response.setHeaderForKey(syncInfo.etag(), "Etag");
      response.setHeaderForKey(isoDateFormatter.format(entry.lastModified()), "Last-Modified");
    }
    return response;
  }
  
  public BlogEntry blogEntryForTitle(String uniqueTitle) {
    BlogEntry entry = (BlogEntry)IERXRestDelegate.Factory.delegateForEntityNamed(BlogEntry.ENTITY_NAME).objectOfEntityWithID(EOClassDescription.classDescriptionForClass(BlogEntry.class), uniqueTitle, _restContext);
    return entry;
  }
  
  @Override
  public WOActionResults updateAction() throws Throwable {
    BlogEntry entry = blogEntryForTitle((String)routeObjectForKey("uniqueTitle"));
    update(entry,inFilter());
    editingContext().saveChanges();
    return response(entry, outFilter());
  }
  
  public WOActionResults destroyAction() throws Throwable {
    BlogEntry entry = blogEntryForTitle((String)routeObjectForKey("uniqueTitle"));
    editingContext().deleteObject(entry);
    editingContext().saveChanges();
    return response(ERXHttpStatusCodes.OK);
  }
  
  @Override
  public WOActionResults indexAction() throws Throwable {
    ERXRestFetchSpecification<BlogEntry> fetchSpec = new ERXRestFetchSpecification<BlogEntry>(BlogEntry.ENTITY_NAME, null, BlogEntry.LAST_MODIFIED.descs());
    fetchSpec.enableRequestQualifiers(null, outFilter());
    restContext().setUserInfoForKey(new NSTimestampFormatter("%Y-%m-%d"), "er.rest.timestampFormatter");
    return response(fetchSpec,outFilter());
  }
  
  public ERXKeyFilter inFilter() {
    ERXKeyFilter filter = ERXKeyFilter.filterWithAttributesAndToOneRelationships();
    filter.include(BlogEntry.CREATED_TIME);
    filter.exclude(BlogEntry.CREATION_TIME);
    filter.include(BlogEntry.CATEGORIES);
    return filter;
  }
  
  public ERXKeyFilter outFilter() {
    return inFilter();
  }
  
  protected ERXRestContext _restContext;

  @Override
  public ERXRestContext restContext() {
    if (_restContext == null) {
      _restContext = super.restContext();
      _restContext.setUserInfoForKey(new NSTimestampFormatter("%Y-%m-%d %H:%M"), "er.rest.timestampFormatter");
    }
    return _restContext;
  }

}
