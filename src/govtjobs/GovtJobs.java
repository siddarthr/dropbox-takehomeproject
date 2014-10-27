/**
 * Author: Siddarth Ramadoss
 * Dropbox take home challenge - Product Manager.
 * Note: Was scrappy when writing this code. Could've used OOP concepts
 * and optimized some parts of the code.
 */
package govtjobs;

import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author siddarthr
 */
public class GovtJobs {

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        String response;
        String agencySubElement;
        String workSchedule;
        String jobTitle;
        String orgName;
        String startDate;
        String endDate;
        String location;
        String city;
        String state;
        String[] jobDetails = new String[8];
        Map<String, String> dept = new HashMap();
        Map<String, Integer> stateDetails = new HashMap();
        Map<String, Integer[]> counts = new HashMap();
        Map<String, Integer[]> periods = new HashMap();
        int[] totalDurations = new int[4];
        int totalJobs = 0;

        int page = 1, numJobs = 0, remainingJobs = 0, index = 0;
        //write to csv
        final String[] header = new String[]{"Dept", "orgName", "agencySubElement",
            "jobTitle", "workSchedule", "daysOpen", "city", "state"};

        CsvMapWriter mapWriter = null;
        mapWriter = new CsvMapWriter(new FileWriter("output.csv"),
                CsvPreference.STANDARD_PREFERENCE);
        // write the header
        mapWriter.writeHeader(header);

        try {

            getDept(dept);
            for (Map.Entry<String, String> entry : dept.entrySet()) {

                String deptCode = entry.getKey();
                String deptVal = entry.getValue();

                do {
                    //get url elements and parse xml
                    URL url = new URL("https://data.usajobs.gov/api/jobs?organizationid=" + deptCode + "&numberofjobs=250&page=" + page);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Content-Type", "application/xml");

                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(connection.getInputStream());

                    doc.getDocumentElement().normalize();

                    //get number of jobs
                    NodeList numJobsElement = doc.getElementsByTagName("ResultsModel");
                    Node countNode = numJobsElement.item(0);
                    Element countNodeElement = (Element) countNode;
                    String countJobs = countNodeElement.getElementsByTagName("TotalJobs").item(0).getTextContent();
                    numJobs = Integer.parseInt(countJobs);
                    totalJobs += numJobs;
                    if (remainingJobs == 0) {
                        remainingJobs = numJobs;
                    }

                    NodeList nList = doc.getElementsByTagName("Job");

                    //go through every job tag in this page
                    for (int temp = 0; temp < nList.getLength(); temp++) {
                        index = 0;
                        Node nNode = nList.item(temp);

                        //extract necessary information from each Job element
                        if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element eElement = (Element) nNode;
                            agencySubElement = eElement.getElementsByTagName("AgencySubElement").item(0).getTextContent();
                            workSchedule = eElement.getElementsByTagName("WorkSchedule").item(0).getTextContent();
                            jobTitle = eElement.getElementsByTagName("JobTitle").item(0).getTextContent();
                            orgName = eElement.getElementsByTagName("OrganizationName").item(0).getTextContent();
                            startDate = eElement.getElementsByTagName("StartDate").item(0).getTextContent();
                            endDate = eElement.getElementsByTagName("EndDate").item(0).getTextContent();
                            location = eElement.getElementsByTagName("Locations").item(0).getTextContent();
                            String role = workSchedule.toLowerCase();
                            if ((role.indexOf("full") != -1) || (role.indexOf("permanent") != -1)) {
                                role = "Full Time";
                            } else if ((role.indexOf("part") != -1) || (role.indexOf("temporary") != -1) || (role.indexOf("intermittent") != -1)) {
                                role = "Part Time";
                            } else {
                                role = "Other";
                            }

                            //init counts for full/part time and others.
                            if (!counts.containsKey(orgName)) {
                                counts.put(orgName, new Integer[3]);
                                Integer[] getCounts = counts.get(orgName);
                                getCounts[0] = new Integer(0);
                                getCounts[1] = new Integer(0);
                                getCounts[2] = new Integer(0);
                            }
                            
                            //init counts for length of jobs (very new, new, old and very old)
                            if (!periods.containsKey(orgName)) {
                                periods.put(orgName, new Integer[4]);
                                Integer[] getCounts = periods.get(orgName);
                                getCounts[0] = new Integer(0);
                                getCounts[1] = new Integer(0);
                                getCounts[2] = new Integer(0);
                                getCounts[3] = new Integer(0);
                            }

                            //get number of days job has been open
                            SimpleDateFormat myFormat = new SimpleDateFormat("MM/dd/yyyy");
                            Date date1 = myFormat.parse(startDate);
                            Date date2 = new Date();
                            long diff = date2.getTime() - date1.getTime();
                            long daysOpen = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);

                            //bucket length of job open to one of 4 categories
                            String timeOpen = null;
                            if (daysOpen < 5) {
                                timeOpen = "Very New";
                            } else if (daysOpen > 5 && daysOpen < 25) {
                                timeOpen = "New";
                            } else if (daysOpen > 25 && daysOpen < 60) {
                                timeOpen = "Old";
                            } else {
                                timeOpen = "Very Old";
                            }

                            //store all details to be written to csv
                            jobDetails[index++] = deptVal;
                            jobDetails[index++] = orgName;
                            jobDetails[index++] = agencySubElement;
                            jobDetails[index++] = jobTitle;
                            jobDetails[index++] = role;
                            jobDetails[index++] = Long.toString(daysOpen);

                            //if multiple locations
                            if (location.indexOf(";") != -1) {
                                //create entry for multiple location
                                String[] splitLoc = location.split(";");
                                for (String loc : splitLoc) {
                                    updateCounts(orgName, role, counts);
                                    updatePeriods(orgName, timeOpen, periods, totalDurations);
                                    processCityState(loc, jobDetails, header, mapWriter);
                                }
                            } //else get city and state
                            else {
                                updateCounts(orgName, role, counts);
                                updatePeriods(orgName, timeOpen, periods, totalDurations);
                                processCityState(location, jobDetails, header, mapWriter);
                            }

                            //if state not in map, create entry
                            String stateName = jobDetails[7];
                            if (!stateDetails.containsKey(stateName)) {
                                stateDetails.put(jobDetails[7], new Integer(1));
                            } else {
                                stateDetails.put(stateName, stateDetails.get(stateName) + 1);
                            }
                        }
                    }
                    remainingJobs -= 250;
                    page++;
                } while (remainingJobs > 0);
                page = 1;
            }
            mapWriter.close();
            
            //print data necessary for google charts API
            for (Map.Entry<String, Integer> entry : stateDetails.entrySet()) {
                System.out.println("['" + entry.getKey() + "'," + entry.getValue() + "],");
            }
            System.out.println("-------");

            for (Map.Entry<String, Integer[]> entry : counts.entrySet()) {
                Integer[] value = entry.getValue();
                System.out.println("['" + entry.getKey() + "'," + value[0] + "," + value[1] + "," + value[2] + "],");
            }
            System.out.println("-------");

            for (Map.Entry<String, Integer[]> entry : periods.entrySet()) {
                Integer[] value = entry.getValue();
                System.out.println("['" + entry.getKey() + "'," + value[0] + "," + value[1] + "," + value[2] + "," + value[3] + "],");
            }
            System.out.println("-------");
            System.out.println(Arrays.toString(totalDurations));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //function to write contents to csv
    private static void writeCSV(String[] jobDetails, String[] header, CsvMapWriter mapWriter) throws IOException {
        Map<String, Object> toWrite = new HashMap<String, Object>();
        toWrite.put(header[0], jobDetails[0]);
        toWrite.put(header[1], jobDetails[1]);
        toWrite.put(header[2], jobDetails[2]);
        toWrite.put(header[3], jobDetails[3]);
        toWrite.put(header[4], jobDetails[4]);
        toWrite.put(header[5], jobDetails[5]);
        toWrite.put(header[6], jobDetails[6]);
        toWrite.put(header[7], jobDetails[7]);

        mapWriter.write(toWrite, header);
    }

    //fetch city and state. then write to csv
    private static void processCityState(String loc, String[] jobDetails, String[] header, CsvMapWriter mapWriter) throws IOException {
        String[] result = getCityState(loc);
        jobDetails[6] = result[0];
        jobDetails[7] = result[1];

        writeCSV(jobDetails, header, mapWriter);
    }

    //split string to city and state
    private static String[] getCityState(String loc) {
        String[] result = new String[2];
        if (loc.indexOf(",") != -1) {
            String[] splitlocation = loc.split(",");
            result[0] = splitlocation[0];
            result[1] = splitlocation[1];
        } else {
            result[0] = loc;
            result[1] = loc;
        }
        return result;
    }

    //get list of all departments to be used when finding jobs
    private static void getDept(Map dept) throws MalformedURLException, IOException, ParserConfigurationException, SAXException {
        URL url = new URL("https://schemas.usajobs.gov/Enumerations/AgencySubElement.xml");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/xml");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(connection.getInputStream());

        doc.getDocumentElement().normalize();

        NodeList nList = doc.getElementsByTagName("ValidValue");
        System.out.println(nList.getLength());

        //go through every job tag in this page
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);

            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                String code = eElement.getElementsByTagName("Code").item(0).getTextContent();
                String name = eElement.getElementsByTagName("Value").item(0).getTextContent();
                String parent = eElement.getElementsByTagName("ParentCode").item(0).getTextContent();
                if (!parent.equals("")) {
                    break;
                }
                dept.put(code, name);
            }
        }
    }

    //update counts of full/part time and other jobs
    private static void updateCounts(String orgName, String role, Map<String, Integer[]> counts) {
        Integer[] getCounts = counts.get(orgName);
        if (role.equals("Full Time")) {
            getCounts[0] += 1;
        } else if (role.equals("Part Time")) {
            getCounts[1] += 1;
        } else {
            getCounts[2] += 1;
        }
    }

    ////keep counts of length of openings of jobs
    private static void updatePeriods(String orgName, String timeOpen, Map<String, Integer[]> periods, int[] totalDurations) {
        Integer[] getCounts = periods.get(orgName);
        if (timeOpen.equals("Very New")) {
            getCounts[0] += 1;
            totalDurations[0] += 1;
        } else if (timeOpen.equals("New")) {
            getCounts[1] += 1;
            totalDurations[1] += 1;
        } else if (timeOpen.equals("Old")) {
            getCounts[2] += 1;
            totalDurations[2] += 1;
        } else {
            getCounts[3] += 1;
            totalDurations[3] += 1;
        }
    }
}
