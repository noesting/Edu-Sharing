
import {TranslateService} from "@ngx-translate/core";
import {isNumeric} from "rxjs/util/isNumeric";
import {Translation} from "../translation";

export class DateHelper{
  /**
   * Fill a date (day + month) string, e.g. 2 -> 02
   * @param date
   * @returns {string}
   */
  private static fillDate(date:number) : string{
    if(date<10)
      return "0"+date;
    return date+"";
  }

  /**
   * Convert date to a unix timestamp
   * @param date
   * @returns {number}
   */
  public static convertDate(date : any){
    return new Date(date).getTime();
  }

  /**
   * format a date with a given, fixed string
   * For consistency, we use the patterns from
   * https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
   * @param date
   * @param {string} format
   * @returns {string}
   */
  public static formatDateByPattern(date: any,format:string) : string{
    if(!isNumeric(date)) {
      return date;
    }
    let dateObject = new Date(date * 1);
    format=format.replace("y",""+dateObject.getFullYear());
    format=format.replace("M",""+DateHelper.fillDate(dateObject.getMonth()));
    format=format.replace("d",""+DateHelper.fillDate(dateObject.getDate()));
    return format;
  }

  /**
   * Format a date for the current language
   * @param translation
   * @param date
   * @param showAlwaysTime
   * @returns {any}
   */
  public static formatDate(translation : TranslateService,date: any,showAlwaysTime=false,useRelativeLabels=true) : string{
    try {
      if(!isNumeric(date)) {
        return date;
      }
      let dateObject = new Date(date * 1);
      let dateToday = new Date();
      let dateYesterday = new Date();
      dateYesterday.setDate(dateYesterday.getDate()-1);
      let isToday = dateObject.toDateString()==dateToday.toDateString();
      let isYesterday = dateObject.toDateString()==dateYesterday.toDateString();
      let prefix = "";
      let timeDiff = dateToday.getTime()-dateObject.getTime();
      let diffDays = Math.ceil(timeDiff / (1000 * 3600 * 24));
      let addDate=true;
      let timeFormat="HH:mm";
      if (isToday && useRelativeLabels) {
        prefix = translation.instant("TODAY");
        addDate = false;
      }
      else if(isYesterday && useRelativeLabels){
        prefix = translation.instant("YESTERDAY");
        addDate = false;
      }
      else if(diffDays<6 && useRelativeLabels){
        prefix = translation.instant("DAYS_AGO",{days:diffDays});
        addDate = false;
        if(!showAlwaysTime)
          timeFormat = "";
      }
      else{
        if(!showAlwaysTime)
          timeFormat="";
      }
      // ng2's dateformatter is super slow, but it doesn't matter, we just iterate it once :)
      //return dateFormat+time;
      let str=prefix;
      if(addDate) {
        if(Translation.getLanguage()=="en"){
          str += dateObject.getFullYear()+"-"+DateHelper.fillDate(dateObject.getMonth()+1)+"-"+DateHelper.fillDate(dateObject.getDate());
        }
        else {
          str += DateHelper.fillDate(dateObject.getDate()) + "." + DateHelper.fillDate(dateObject.getMonth() + 1) + "." + dateObject.getFullYear();
        }
        //str += DateFormatter.format(dateObject, Translation.getLanguage(), dateFormat).trim();
      }
      // ie fixes, timeFormat not working
      if(timeFormat){
        if(str)
          str+=", ";
        let timeValue=dateObject.toLocaleTimeString(Translation.getLanguage());
        let times=timeValue.split(":");
        str += timeFormat.replace("HH",times[0]).replace("mm",times[1]).replace("ss",times[2]);
      }
      return str;
      /*
      let dateString=prefix+" ";
      if(dateFormat!=""){
        dateString+=dateObject.toLocaleDateString(Translation.getLanguage())+" ";
      }
      if(time!=""){
        dateString+=dateObject.toLocaleTimeString(Translation.getLanguage());
      }
      return dateString;
      */
    }
    catch(e){
      return (date as string);
    }
  }
  static getDateFromDatepicker(date:Date){
    return new Date(date.getTime()-date.getTimezoneOffset()*60*1000);
  }

}
