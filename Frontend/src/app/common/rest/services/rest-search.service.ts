import { Injectable } from '@angular/core';
import { Http, Response } from '@angular/http';
import 'rxjs/add/operator/map'
import { Observable } from 'rxjs/Observable';
import {RestConnectorService} from "./rest-connector.service";
import {RestHelper} from "../rest-helper";
import {RestConstants} from "../rest-constants";
import { NodeRef, Node, NodeWrapper, NodePermissions, LocalPermissions, NodeVersions, NodeVersion, NodeList} from "../data-object";
import {RequestObject} from "../request-object";
import {AbstractRestService} from "./abstract-rest-service";

@Injectable()
export class RestSearchService extends AbstractRestService{
  constructor(connector : RestConnectorService) {
      super(connector);
  }

  searchByProperties(properties:string[],values:string[],comparators:string[],combineMode=RestConstants.COMBINE_MODE_AND,contentType=RestConstants.CONTENT_TYPE_FILES,request: any=null, repository = RestConstants.HOME_REPOSITORY) : Observable<NodeList> {
    let url=this.connector.createUrlNoEscape('search/:version/custom/:repository?contentType=:contentType&combineMode=:combineMode&:properties&:values&:comparators&:request',repository,[
      [":contentType",contentType],
      [":combineMode",combineMode],
      [":properties",RestHelper.getQueryString("property",properties)],
      [":values",RestHelper.getQueryString("value",values)],
      [":comparators",RestHelper.getQueryString("comparator",comparators)],
      [":request",this.connector.createRequestString(request)]
    ]);
    return this.connector.get(url,this.connector.getRequestOptions()).map((response: Response) => response.json());

  }
  saveSearch(name:string,query:string,criterias:any[],repository = RestConstants.HOME_REPOSITORY, metadataset = RestConstants.DEFAULT,replace=false) : Observable<NodeWrapper> {
    let url=this.connector.createUrl('search/:version/queriesV2/:repository/:metadataset/:query/save?name=:name&replace=:replace',repository,[
      [":name",name],
      [":query",query],
      [":metadataset",metadataset],
      [":replace",""+replace],
    ]);
    return this.connector.post(url,JSON.stringify(criterias),this.connector.getRequestOptions()).map((response: Response) => response.json());

  }
  searchSimple(queryId = 'ngsearch',criterias: any[]=[],query:string=null,request: any=null,type=RestConstants.CONTENT_TYPE_FILES) : Observable<NodeList> {
    if(query){
      criterias.push({property:RestConstants.PRIMARY_SEARCH_CRITERIA,values:[query]});
    }
    return this.search(criterias,null,request,type, RestConstants.HOME_REPOSITORY,RestConstants.DEFAULT,[],queryId);
  }
    search(criterias: any[],facettes:string[]=[], request: any=null,contentType=RestConstants.CONTENT_TYPE_FILES, repository = RestConstants.HOME_REPOSITORY, metadataset = RestConstants.DEFAULT,propertyFilter:string[]=[], query = 'ngsearch') : Observable<NodeList> {
        let properties = '';
        for(var i = 0; i < criterias.length; i++) {
            if(i > 0)
              properties += ',';
            properties += '{"property":"'+criterias[i]['property']+'","values":'+JSON.stringify(criterias[i]['values'])+'}';
        }
        let body = '{"criterias":[' + properties + ']' + ',"facettes":'+JSON.stringify(facettes)+'}';

      let q=this.connector.createUrlNoEscape('search/:version/queriesV2/:repository/:metadataset/:query/?contentType=:contentType&:request&:propertyFilter',repository,[
        [":metadataset",encodeURIComponent(metadataset)],
        [":query",encodeURIComponent(query)],
        [":contentType",contentType],
        [":propertyFilter",RestHelper.getQueryString("propertyFilter",propertyFilter)],
        [":request",this.connector.createRequestString(request)]
      ]);
      return this.connector.post(q,body,this.connector.getRequestOptions()).map((response: Response) => response.json());
    }

  static isSavedSearchObject(node: Node) {
    return node.mediatype=='saved_search';
  }
}
