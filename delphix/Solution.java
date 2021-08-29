package com.delphix.interview;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;

import java.io.IOException;

public class Solution {


    private static final String URL_STRING = "https://ssd-api.jpl.nasa.gov/fireball.api";
    private static final int LOCATION_BUFFER_DISTANCE = 15;


    public static void main(String[] args) throws FireBallException, FireBallValidationException {


        List<BrightStar> brightestStarList = new ArrayList<>();

        //call for boston
        Optional<StarData> bostonStarData = fireBall("42.354558N", "71.054254W");
        bostonStarData.ifPresent(starData -> {
            brightestStarList.add(new BrightStar("BOSTON", starData));

        });
        //call for ncr
        Optional<StarData> ncrStarData = fireBall("28.574389N", "77.312638E");
        ncrStarData.ifPresent(starData -> {
            brightestStarList.add(new BrightStar("NCR",starData));
        });

        //call for sf
        Optional<StarData> sfStarData = fireBall("37.793700N", "122.403906W");
        sfStarData.ifPresent(starData -> {
            brightestStarList.add(new BrightStar("SF",starData));
        });


        Optional<BrightStar> brightestStar = brightestStarList.stream()
                                                            .sorted(Comparator.comparing(brightStar -> {
                                                                    return  brightStar.getStarData().getEnergy();
                                                                },Comparator.reverseOrder()))
                                                            .findFirst();

        brightestStar.ifPresent(sd-> System.out.println("location="+ sd.getOfficeLocation() + ",latitude" + sd.getStarData().getLat() + ",lonitude=" + sd.getStarData().getLon() + ",energy=" + sd.getStarData().getEnergy()));

    }




    public static Optional<StarData> fireBall(String latitude, String longitude) throws FireBallValidationException , FireBallException{
        double lat, lng;
        String latDir,lngDir;
        try {
             lat = Double.valueOf(latitude.substring(0, latitude.length() - 1));
             latDir = latitude.substring(latitude.length() - 1);
             lng = Double.valueOf(longitude.substring(0, longitude.length() - 1));
             lngDir = longitude.substring(longitude.length() - 1);


        } catch (Exception e){
            throw new FireBallValidationException("fireball validation exception");
        }

        // can be configurable
        String dateQueryParam = "date-min=2017-01-01&date-max=2020-01-01&req-loc=true";
        //its better to call api for each individual request
        FireballResponse fireballResponse =  callFireBallApi(dateQueryParam);

        Optional<StarData> stardata = filterTheBrightestStarInBufferRange(lat,lng,fireballResponse);
        return  stardata;
        //return  stardata.isPresent()? stardata.get(): null;
    }



    private static Optional<StarData> filterTheBrightestStarInBufferRange(double latitude, double longitude, FireballResponse fireballResponse){
        if(null != fireballResponse && fireballResponse.getCount() == 0)
            return null;
        double xMinus = latitude - LOCATION_BUFFER_DISTANCE;
        double xPlus = latitude + LOCATION_BUFFER_DISTANCE;
        double yMinus = longitude - LOCATION_BUFFER_DISTANCE;
        double yPlus = longitude + LOCATION_BUFFER_DISTANCE;


      Optional<StarData> data =  fireballResponse.getData()
                                                 .stream()
                                                 .filter(d -> {
                                                     return  ((Double.valueOf(d.getLat()) > xMinus && Double.valueOf(d.getLat()) < xPlus)
                                                             && (Double.valueOf(d.getLon()) > yMinus && Double.valueOf(d.getLon()) < yPlus));

                                                })
                                                 .sorted(Comparator.comparing(StarData::getEnergy,Comparator.reverseOrder()))
                                                 .findFirst();
      return data;

    }

    private static FireballResponse callFireBallApi(String queryParam) throws FireBallException {
        FireballResponse apiResponseData ;
        URL url ;
        try {
            if(null != queryParam)
                queryParam = "?" + queryParam;
            url = new URL(URL_STRING + queryParam);
            HttpURLConnection con =  (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            apiResponseData =  getFormatedResponse(con);
        } catch (Exception e) {
            throw new FireBallException(e.getMessage());
        }
        return  apiResponseData;

    }

    /*private static final HttpURLConnection getConnection(URL entries) throws InterruptedException, IOException {
        int retry = 0;
        int RETRIES = 3;
        long RETRY_DELAY_MS = 30;
        boolean delay = false;
        HttpURLConnection connection =null;
        do {
            if (delay) {
                Thread.sleep(RETRY_DELAY_MS);
            }
             connection = (HttpURLConnection)entries.openConnection();
            switch (connection.getResponseCode()) {
                case HttpURLConnection.HTTP_OK:
                    return connection; // **EXIT POINT** fine, go on
                case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
                   break;// retry
                case HttpURLConnection.HTTP_UNAVAILABLE:
                    break;// retry, server is unstable
                default:
                    break; // abort
            }
            // we did not succeed with connection (or we would have returned the connection).
            connection.disconnect();
            // retry
            retry++;
            delay = true;

        } while (retry < RETRIES);
        return connection;
    }*/

    private static FireballResponse getFormatedResponse(HttpURLConnection con) throws IOException {


        BufferedReader streamReader = null;
        StringBuilder content = null;
        try {
            if (con.getResponseCode() > 299) {
                streamReader = new BufferedReader(new InputStreamReader(con.getErrorStream()));
            } else {
                streamReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            }
            String inputLine;
            content = new StringBuilder();
            while ((inputLine = streamReader.readLine()) != null) {
                content.append(inputLine);
            }
        } finally {
            if(streamReader != null)
                streamReader.close();
        }

        FireballResponse fireballResponse = null;
        if(con.getResponseCode() > 299){
            throw new FireBallException(content.toString());
        }else if(con.getResponseCode() == HttpURLConnection.HTTP_OK)
            fireballResponse = getFireballResponse(content.toString());
        return fireballResponse;

    }

    private static FireballResponse getFireballResponse(String content){
        FireballResponse fireballResponse = null;

        try {
            JSONParser parser = new JSONParser();
            JSONObject object1 = (JSONObject) parser.parse(String.valueOf(content));
            JSONArray data = (JSONArray)object1.get("data");
            JSONArray fields = (JSONArray)object1.get("fields");
            JSONObject signature = (JSONObject) object1.get("signature");
            long count = Long.valueOf((String) object1.get("count"));
            fireballResponse = new FireballResponse();
            List<StarData> starDataList = new ArrayList<>();
            for (Object j: data.toArray()){
                StarData starDataRecord = getStarDataFromJson(fields,j);
                starDataList.add(starDataRecord);
            }

            fireballResponse.setSignature(new Signature(String.valueOf(signature.get("source")), String.valueOf(signature.get("version"))));
            fireballResponse.setCount(count);
            fireballResponse.setData(starDataList);
            List<String> fieldList = new ArrayList<>();
            for(Object obj : fields){
                fieldList.add(String.valueOf(obj));
            }
            fireballResponse.setFields(fieldList);
        } catch ( ParseException e) {
            throw  new FireBallException(e.getMessage());
        }

        return  fireballResponse;
    }


    private static StarData getStarDataFromJson(JSONArray fields,Object obj){
        StarData starDataRecord = new StarData();

        if(fields.contains("date") ){
            String date = (String) ((JSONArray) obj).get(fields.indexOf("date"));
            starDataRecord.setDate(date);
        }
        if(fields.contains("energy") ){
            String energy = (String) ((JSONArray) obj).get(fields.indexOf("energy"));
            starDataRecord.setEnergy(Double.valueOf(energy));
        }
        if(fields.contains("impact-e") ){
            String impactE = (String) ((JSONArray) obj).get(fields.indexOf("impact-e"));
            starDataRecord.setImpactE(impactE);
        }
        if(fields.contains("lat") ){
            String lat = (String) ((JSONArray) obj).get(fields.indexOf("lat"));
            starDataRecord.setLat(Double.valueOf(lat));
        }
        if(fields.contains("lat-dir") ){
            String latDir = (String) ((JSONArray) obj).get(fields.indexOf("lat-dir"));
            starDataRecord.setLatDir(latDir);
        }
        if(fields.contains("lon") ){
            String lon = (String) ((JSONArray) obj).get(fields.indexOf("lon"));
            starDataRecord.setLon(Double.valueOf(lon));
        }
        if(fields.contains("lon-dir") ){
            String lonDir = (String) ((JSONArray) obj).get(fields.indexOf("lon-dir"));
            starDataRecord.setLonDir(lonDir);
        }
        if(fields.contains("alt") ){
            String alt = (String) ((JSONArray) obj).get(fields.indexOf("alt"));
            starDataRecord.setAlt(alt);
        }
        if(fields.contains("vel") ){
            String vel = (String) ((JSONArray) obj).get(fields.indexOf("vel"));
            starDataRecord.setVel(vel);
        }
        if(fields.contains("vx") ){
            String vx = (String) ((JSONArray) obj).get(fields.indexOf("vx"));
            starDataRecord.setVx(vx);
        }
        if(fields.contains("vy") ){
            String vy = (String) ((JSONArray) obj).get(fields.indexOf("vy"));
            starDataRecord.setVy(vy);
        }
        if(fields.contains("vz") ){
            String vz = (String) ((JSONArray) obj).get(fields.indexOf("vz"));
            starDataRecord.setVz(vz);
        }
        return starDataRecord;
    }
}


class  FireballController{
    

}
interface   FireballService{

}
class FireballServiceImpl implements  FireballService{

}
class FireballResponse {

    private Signature signature;
    private long count;
    private List<String> fields = new ArrayList<>(); 
    private List<StarData> data = new ArrayList<>();

    public Signature getSignature() {
        return signature;
    }

    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public List<StarData> getData() {
        return data;
    }

    public void setData(List<StarData> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "FireballResponse{" +
                "signature=" + signature +
                ", count='" + count + '\'' +
                ", fields=" + fields +
                ", data=" + data +
                '}';
    }
}

 class Signature {

    private String source;
    private String version;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

     public Signature(String source, String version) {
         this.source = source;
         this.version = version;
     }
 }

 class BrightStar {

    private String officeLocation;
    private StarData starData;

     public String getOfficeLocation() {
         return officeLocation;
     }

     public void setOfficeLocation(String officeLocation) {
         this.officeLocation = officeLocation;
     }

     public StarData getStarData() {
         return starData;
     }

     public void setStarData(StarData starData) {
         this.starData = starData;
     }

     public BrightStar(String officeLocation, StarData starData) {
         this.officeLocation = officeLocation;
         this.starData = starData;
     }
 }

class StarData {

    private String date;
    private double energy;
    private String impactE;
    private double lat;
    private String latDir;
    private double lon;
    private String lonDir;
    private String alt;
    private String vel;
    private String vx;
    private String vy;
    private String vz;


    public StarData() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public double getEnergy() {
        return energy;
    }

    public void setEnergy(double energy) {
        this.energy = energy;
    }

    public String getImpactE() {
        return impactE;
    }

    public void setImpactE(String impactE) {
        this.impactE = impactE;
    }

    public String getLatDir() {
        return latDir;
    }

    public void setLatDir(String latDir) {
        this.latDir = latDir;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public String getLonDir() {
        return lonDir;
    }

    public void setLonDir(String lonDir) {
        this.lonDir = lonDir;
    }

    public String getAlt() {
        return alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public String getVel() {
        return vel;
    }

    public void setVel(String vel) {
        this.vel = vel;
    }

    public String getVx() {
        return vx;
    }

    public void setVx(String vx) {
        this.vx = vx;
    }

    public String getVy() {
        return vy;
    }

    public void setVy(String vy) {
        this.vy = vy;
    }

    public String getVz() {
        return vz;
    }

    public void setVz(String vz) {
        this.vz = vz;
    }

    @Override
    public String toString() {
        return "Data{" +
                "date='" + date + '\'' +
                ", energy='" + energy + '\'' +
                ", lat='" + lat + '\'' +
                ", latDir='" + latDir + '\'' +
                ", lon='" + lon + '\'' +
                ", lonDir='" + lonDir + '\'' +
                ", alt='" + alt + '\'' +
                '}';
    }
}
class FireBallException extends RuntimeException {
    public FireBallException(String message) {
        super(message);
    }
}
class FireBallValidationException extends Exception {
    public FireBallValidationException(String message) {
        super(message);
    }
}


// test case
// api related integration test case
    /*
    HTTP 200k
    HTTP 400, 404
    HTTP 500
     */
// if 200 OK response and count is 0 in the response from api
// if 200 OK response and count is > 0 and no records for the filter criteria i.e latitude and longitude
// if 200 OK response but structure of response is changed
// request params i.e latitude and longitude are not formatted well or null

