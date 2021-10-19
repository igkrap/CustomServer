package secomdalcom;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

public class Custom_Server extends Thread {
    static ArrayList<Custom_Server> clients = new ArrayList<Custom_Server>();
    Socket socket;
    String type="",name="";
    static DBAccessor dbAccessor;
    static SecurityModule securityModule;
    static boolean SecurityMode=false;
    String IP="";

    public Custom_Server(Socket socket) throws Exception {
        this.socket = socket;
        this.IP=this.socket.getInetAddress().getHostAddress();
        this.socket.setSoTimeout(15000);

         // 읽기
    }
    @Override
    public void run() {
        try {
            while (true) {
                String rawdata;
                InputStream input= socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));

                if ((rawdata = reader.readLine()) != null) {
                    System.out.println(rawdata);
                    System.out.println(StringColor.ANSI_GREEN+"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"+StringColor.ANSI_RESET);
                    System.out.println(StringColor.ANSI_WHITE+"메시지 감지"+StringColor.ANSI_RESET);
                    //System.out.println("받은 메시지:"+data);
//                    for (Custom_Server c: clients) {
//                        c.send(s);
//                    }
                    //JSONObject safeData = new JSONObject(data);
                    JSONObject data;
                    if(SecurityMode) data=securityModule.executeSecurityProcess(this,rawdata);
                    else data=new JSONObject(rawdata);
//                    System.out.println(jObject.getString("SENDER_TYPE")+" "+jObject.getString("MESSAGE_TYPE")+" "+jObject.getString("NAME"));
                    if(data.getString("MESSAGE_TYPE").equals("LOGIN_REQUEST")){
                        if(SecurityMode)securityModule.login_s(this,data);
                        else login(data);
                        break;
                    }
                    switch (data.getString("SENDER_TYPE")) {
                        case "USER":
                            this.receiveUserRequest(data);
                            break;
                        case "SENSOR":
                            this.receiveSensorData(data);
                            break;
                        case "ACTUATOR":
                            this.receiveActuatorData(data);
                            break;
                        case "COMPOUND":
                            this.receiveCompoundData(data);
                            break;
                        default:
                            throw new Exception("SENDER_TYPE_ERROR");
                    }
                    if (SecurityMode) {
                        if (!data.getString("MESSAGE_TYPE").equals("ALIVE")) {
                            dbAccessor.saveLog(this.IP, this.name, data.getString("SENDER_TYPE"), "COMMON", "", data.getString("MESSAGE_TYPE"));
                        }
                        System.out.println(StringColor.ANSI_GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + StringColor.ANSI_RESET);
                    }
                    }
            }
            }
     catch (Exception e) {
            System.out.println(StringColor.ANSI_YELLOW+this.name+"의 접속이 종료되었습니다."+StringColor.ANSI_RESET);
            // TODO Auto-generated catch block
            try {
                e.printStackTrace();
                socket.close();
                clients.remove(this);
                Statement stmt=dbAccessor.con.createStatement();
                System.out.println(StringColor.ANSI_YELLOW+"남은 클라이언트 숫자:"+clients.size()+StringColor.ANSI_RESET);
                JSONObject jsonObject=new JSONObject();
                jsonObject.put("SENDER_TYPE", "SERVER");
                jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject.put("FORM_TYPE","MANAGE");
                JSONObject jsonObject2=new JSONObject();
                jsonObject2.put("SENDER_TYPE", "SERVER");
                jsonObject2.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject2.put("FORM_TYPE","DASHBOARD");
                if(this.type.equals("ACTUATOR")||this.type.equals("SENSOR")||this.type.equals("COMPOUND")) {
                    String sql="UPDATE MACHINE SET CONNECT_STATUS='DISCONNECT' WHERE NAME='"+this.name+"'";
                    stmt.executeUpdate(sql);
                    jsonObject.put("DATA_TYPE", "MACHINE");
                    jsonObject.put("CONNECT_STATUS",dbAccessor.findMachineStatusByName(this.name));
                    jsonObject2.put("DATA_TYPE", "MACHINE_CONNECT");
                    jsonObject2.put("CONNECT_STATUS",dbAccessor.findMachineStatusByName(this.name));
                    jsonObject2.put("NAME",this.name);
                    sendToUser(jsonObject2);
                }
                else if(this.type.equals("USER")){
                    String sql="UPDATE USER SET CONNECT_STATUS='DISCONNECT',ACCESSFORM='-' WHERE USERNAME='"+this.name+"'";
                    stmt.executeUpdate(sql);
                    jsonObject.put("DATA_TYPE","USER");
                    jsonObject.put("CONNECT_STATUS",dbAccessor.findUserStatusByName(this.name));
                }
                jsonObject.put("NAME",this.name);
                sendToUser(jsonObject);

            } catch (Exception e2) {

                e2.printStackTrace();
            }

        }
    }

    public void searchLog(JSONObject jo) throws Exception {

        System.out.print("["+this.IP+"] 유저명 : "+this.name+"    "+jo.getString("FORM_TYPE")+"에서 로그 조회 요청");
        String sql="SELECT * FROM LOG WHERE 1=1";

        JSONObject query= jo.getJSONObject("QUERY");
        System.out.println(query.toString());
        Iterator x = query.keys();
        while (x.hasNext()){
            String key = (String) x.next();
            if(query.getString(key).equals("")) continue;
            if(key.equals("TIMESTAMP1")){
                sql+=" AND TIMESTAMP>='"+query.getString(key)+"'";
            }
            else if(key.equals("TIMESTAMP2")){
                sql+=" AND TIMESTAMP<='"+query.getString(key)+"'";
            }
            else {
                sql+=" AND "+key+"='"+query.getString(key)+"'";
            }
        }
        System.out.println("생성된 쿼리: "+sql);
        Statement stmt=dbAccessor.con.createStatement();
        JSONArray resultArray=new JSONArray();
        ResultSet rs=stmt.executeQuery(sql);
        while(rs.next()){
            JSONObject query_result=new JSONObject();
            query_result.put("ACCESS_IP",rs.getString(2));
            query_result.put("CLIENT_TYPE",rs.getString(3));
            query_result.put("LOG_LEVEL",rs.getString(4));
            query_result.put("INFORMATION",rs.getString(5));
            query_result.put("ACTIVITY",rs.getString(6));
            query_result.put("TIMESTAMP",rs.getString(7));
            resultArray.put(query_result);
        }
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "FORM_DATA");
        response.put("FORM_TYPE","SEARCH");
        response.put("DATA_TYPE","LOG");
        response.put("DATA",resultArray);
        send(response.toString());
    }


    public static void sendToUser(JSONObject jo) throws Exception {
        for (Custom_Server c :clients){
            if(c.type.equals("USER")) {
                if(jo.getString("FORM_TYPE").equals(dbAccessor.findUserLocationById(c.name))){
                    c.send(jo.toString());
                }
            }
        }
    }

    void init(JSONObject jo) throws Exception {
        clients.add(this);
        System.out.print(StringColor.ANSI_WHITE+"["+this.IP+"]"+StringColor.ANSI_RESET);
        this.type=jo.getString("SENDER_TYPE");
        this.name=jo.getString("NAME");
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "INIT_OK");

        if(this.type.equals("ACTUATOR")||this.type.equals("SENSOR")||this.type.equals("COMPOUND"))
        {
            System.out.print("(기기명 : "+this.name+")\t");
            System.out.println(StringColor.ANSI_GREEN+"접속승인"+StringColor.ANSI_RESET );

                String sql="UPDATE MACHINE SET CONNECT_STATUS='CONNECT' WHERE NAME='"+this.name+"'";
                Statement stmt= dbAccessor.con.createStatement();
                stmt.executeUpdate(sql);
                JSONObject jsonObject=new JSONObject();
                jsonObject.put("SENDER_TYPE", "SERVER");
                jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject.put("FORM_TYPE","MANAGE");
                jsonObject.put("DATA_TYPE", "MACHINE");
                jsonObject.put("NAME",this.name);
                jsonObject.put("CONNECT_STATUS",dbAccessor.findMachineStatusByName(this.name));
                sendToUser(jsonObject);
            jsonObject=new JSONObject();
            jsonObject.put("SENDER_TYPE", "SERVER");
            jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
            jsonObject.put("FORM_TYPE","DASHBOARD");
            jsonObject.put("DATA_TYPE", "MACHINE_CONNECT");
            jsonObject.put("NAME",this.name);
            jsonObject.put("CONNECT_STATUS",dbAccessor.findMachineStatusByName(this.name));
            sendToUser(jsonObject);
        }
        else{
            response.put("ROLE", dbAccessor.findUserRoleByToken(jo.getString("TOKEN")));

            System.out.print("(유저명 : "+this.name+")\t");
            System.out.print(StringColor.ANSI_GREEN+"접속승인"+StringColor.ANSI_RESET );

            String sql="UPDATE USER SET CONNECT_STATUS='CONNECT' WHERE USERNAME='"+this.name+"'";
            Statement stmt= dbAccessor.con.createStatement();

            stmt.executeUpdate(sql);
            JSONObject jsonObject=new JSONObject();
            jsonObject.put("SENDER_TYPE", "SERVER");
            jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
            jsonObject.put("FORM_TYPE","MANAGE");
            jsonObject.put("DATA_TYPE", "USER");
            jsonObject.put("NAME",this.name);
            jsonObject.put("CONNECT_STATUS",dbAccessor.findUserStatusByName(this.name));
            send(response.toString());
            sendToUser(jsonObject);
        }
        System.out.println(StringColor.ANSI_WHITE+"<접속자 수:"+clients.size()+">"+StringColor.ANSI_RESET);
//            String sql="SELECT * FROM MACHINE";
//            stmt=con.createStatement();
//            rs=stmt.executeQuery(sql);
//            while(rs.next()){
//                if SENDER_TYPErs.getString(4);
//            }
        //dbAccessor.saveLog(jo.getString("IP"),this.name,jo.getString("SENDER_TYPE"),"COMMON","",jo.getString("MESSAGE_TYPE"));

    }

    void send(String data) throws Exception {
        if(socket.isClosed()) return;
        if(SecurityMode) data=securityModule.encryptData(data);
        OutputStream out = socket.getOutputStream(); // 쓰기
        OutputStreamWriter osw1 = new OutputStreamWriter(out, "utf-8");
        PrintWriter writer = new PrintWriter(osw1, true); //
        writer.println(data);
    }
    void orderOre(JSONObject jo) throws Exception {
        System.out.print("["+this.IP+"] 유저명 : "+this.name+"    "+jo.getString("FORM_TYPE")+"에서 원자재 주문");
        String amount=jo.getString("COAL");
        String sql= "UPDATE ORE SET AMOUNT = AMOUNT+"+amount+" WHERE ID ="+2;
        Statement stmt=dbAccessor.con.createStatement();
        stmt.executeUpdate(sql);
        amount=jo.getString("IRON");
        sql="UPDATE ORE SET AMOUNT = AMOUNT+"+amount+" WHERE ID ="+1;
        stmt=dbAccessor.con.createStatement();
        stmt.executeUpdate(sql);
        multiCastOreData();
    }

    void chooseCountryResponse(JSONObject jo) throws Exception {
        String country=jo.getString("COUNTRY");

        System.out.print("["+this.IP+"] 유저명 : "+this.name+"    "+jo.getString("FORM_TYPE")+"에서 "+country+" 원자재 데이터 요청");
        String sql="SELECT * FROM ORE_PRICE_TREND WHERE COUNTRY='"+country+"'";
        Statement stmt = dbAccessor.con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE","FORM_DATA");
        response.put("FORM_TYPE","ORE");
        response.put("DATA_TYPE","COUNTRY");
        while(rs.next()) {
            String code = rs.getString(3).substring(0, 1) + rs.getString(4).substring(0, 1);
            response.put(code, rs.getInt(5));
        }
        send(response.toString());
    }
    void multiCastOreData() throws Exception {
        String sql="SELECT * FROM ORE";
        Statement stmt= dbAccessor.con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE","FORM_DATA");
        response.put("FORM_TYPE","ORE");
        response.put("DATA_TYPE","AMOUNT");
        while(rs.next()) {
            response.put(rs.getString(2),rs.getInt(3));
        }
        sendToUser(response);
//        for (Custom_Server c :clients){
//            if(c.type.equals("user")) {
//                c.send(response.toString());
//            }
//        }
    }
//    void broadC

    void login(JSONObject jo) throws Exception {
        int COUNT=0;
        String id = jo.getString("ID");
        String password = jo.getString("PASSWORD");

        Statement stmt = dbAccessor.con.createStatement();
        String sql = "SELECT count(*) FROM USER WHERE "+"USERNAME = '"+id+"' AND PASSWORD = '"+password+"'";
        ResultSet rs = stmt.executeQuery(sql);
        while(rs.next()){
            COUNT = rs.getInt(1);
        }
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "LOGIN_RESPONSE");
        if (COUNT>=1)
        {
            System.out.println("성공");
            String token=securityModule.createToken(id);
            sql="UPDATE USER SET TOKEN ='"+token+"' WHERE USERNAME ='"+id+"'";
            stmt.executeUpdate(sql);
            response.put("RESPONSE_CODE", 1);
            response.put("TOKEN", token);
            send(response.toString());
        }
        else {
            System.out.println("실패");
            response.put("RESPONSE_CODE", 2);
            send(response.toString());
            socket.close();
        }
    }
    void enter(JSONObject jo) throws Exception {
        String AccessForm=jo.getString("FORM_TYPE");
        System.out.println("["+this.IP+"] 유저명 : "+this.name+"    "+AccessForm+" 폼에 접근");
        String sql="UPDATE USER SET ACCESSFORM='"+AccessForm+"' WHERE USERNAME ='"+this.name+"'";
        Statement stmt=dbAccessor.con.createStatement();
        stmt.executeUpdate(sql);
        ResultSet rs;
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "FORM_DATA");
        response.put("FORM_TYPE", AccessForm);
        response.put("DATA_TYPE","INITIALIZATION");
        switch (AccessForm){
            case "DASHBOARD":
                sql="SELECT RATE FROM PROCESS";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    response.put("PROCESS_RATE",rs.getInt(1));
                }
                sql="SELECT * FROM ORE";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    response.put(rs.getString(2),rs.getInt(3));
                }
                sql="SELECT * FROM ORE_USED";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    response.put(rs.getString(2)+"_USED",rs.getInt(3));
                }
                sql="SELECT * FROM ORE_PROCESSED WHERE TERM='TODAY'";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    response.put("SUCCESS",rs.getInt(3));
                    response.put("FAILURE",rs.getInt(4));
                }
                JSONArray machinesArray=new JSONArray();
                sql="SELECT * FROM MACHINE";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    JSONObject jsonObject=new JSONObject();
                    jsonObject.put("NAME",rs.getString(3));
                    jsonObject.put("CONNECT_STATUS",rs.getString(6));
                    if(rs.getString(2).equals("SENSOR")||rs.getString(2).equals("ACTUATOR")){
                        if(rs.getString(2).equals("ACTUATOR")) {jsonObject.put("POWER",rs.getString(7));}
                        jsonObject.put("VALUE",rs.getString(8));
                    }
                    machinesArray.put(jsonObject);
                }
                response.put("MACHINE",machinesArray);
                send(response.toString());
                break;
            case "ORE":
                sql = "SELECT * FROM ORE_PRICE_TREND";
                rs = stmt.executeQuery(sql);
                while(rs.next()){
                        if(rs.getString(4).equals("THISMONTH")) {
                            String code = rs.getString(2).substring(0, 2) + rs.getString(3).substring(0, 1);
                            response.put(code, rs.getInt(5));
                        }
                }
                sql = "SELECT * FROM ORE";
                rs =stmt.executeQuery(sql);
                while(rs.next()){
                    response.put(rs.getString(2)+"_AMOUNT",rs.getInt(3));
                }
                send(response.toString());
                break;
            case "CONTROL":
                sql= "SELECT * FROM MACHINE WHERE TYPE='ACTUATOR'";
                rs = stmt.executeQuery(sql);
                while(rs.next()){
                    response.put(rs.getString(3), rs.getString(7)+"_"+rs.getInt(8));
                }
                send(response.toString());
                break;
            case "MANAGE":
                JSONArray machineArray=new JSONArray();
                JSONArray userArray=new JSONArray();
                JSONArray logArray=new JSONArray();

                sql="SELECT * FROM MACHINE";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    JSONObject jsonObject=new JSONObject();
                    jsonObject.put("ID",rs.getString(1));
                    jsonObject.put("TYPE",rs.getString(2));
                    jsonObject.put("NAME",rs.getString(3));
                    jsonObject.put("MAC_ADDRESS",rs.getString(4));
                    jsonObject.put("IP",rs.getString(5));
                    jsonObject.put("CONNECT_STATUS",rs.getString(6));
                    machineArray.put(jsonObject);
                }
                sql="SELECT * FROM USER";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    JSONObject jsonObject=new JSONObject();
                    jsonObject.put("ID",rs.getString(1));
                    jsonObject.put("NAME",rs.getString(2));
                    jsonObject.put("ROLE",rs.getString(5));
                    jsonObject.put("CONNECT_STATUS",rs.getString(8));
                    jsonObject.put("ACCOUNT_STATUS",rs.getString(9));
                    userArray.put(jsonObject);
                }
                sql="SELECT * FROM (SELECT * FROM LOG ORDER BY IDX DESC LIMIT 30) l order by IDX";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    JSONObject jsonObject=new JSONObject();
                    jsonObject.put("DATA",rs.getString("TIMESTAMP")+"|["+rs.getString("ACCESS_IP")+"] " +rs.getString("CLIENT_TYPE")+" "+rs.getString("LOG_LEVEL")+" "+rs.getString("INFORMATION")+" "+rs.getString("ACTIVITY"));
                    logArray.put(jsonObject);
                }
                response.put("MACHINE",machineArray);
                response.put("USER",userArray);
                response.put("LOG",logArray);
                send(response.toString());
                default:
                break;
        }
    }


    void receiveUserRequest(JSONObject jo) throws Exception {
        switch(jo.getString("MESSAGE_TYPE")) {
            case "INIT":
                init(jo);
                break;
            case "ENTER_FORM_REQUEST":
                enter(jo);
                break;
            case "ORE_ORDER_REQUEST":
                orderOre(jo);
                break;
            case "ORE_COUNTRY_REQUEST":
                chooseCountryResponse(jo);
                break;
            case "CONTROL_REQUEST":
                controlMachine(jo);
                break;
            case "MANAGE_USER_REQUEST":
                manageUser(jo);
                break;
            case "MANAGE_MACHINE_REQUEST":
                manageMachine(jo);
                break;
            case "SEARCH_LOG_REQUEST":
                searchLog(jo);
                break;
            case "ALIVE":
                break;
            default :
                break;
        }
    }

    void manageUser(JSONObject jo){}
    void manageMachine(JSONObject jo){}
    void controlMachine(JSONObject jo) throws Exception {
        System.out.println("["+this.IP+"] 유저명 : "+this.name+"    "+jo.getString("FORM_TYPE")+"에서 기기제어 요청");
        Statement stmt=dbAccessor.con.createStatement();
        String sql= "UPDATE MACHINE SET POWER= '"+jo.getString("POWER")+"'" +
                ", VALUE=" +jo.getInt("VALUE")+
                " WHERE NAME='"+jo.getString("TARGET")+"'";

        stmt.executeUpdate(sql);
        sql="SELECT * FROM MACHINE WHERE NAME='"+jo.getString("TARGET")+"'";
        ResultSet rs=stmt.executeQuery(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE","FORM_DATA");
        response.put("FORM_TYPE","CONTROL");
        response.put("DATA_TYPE","CONDITION");
        JSONObject jsonObject=new JSONObject();
        jsonObject.put("SENDER_TYPE", "SERVER");
        jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
        jsonObject.put("FORM_TYPE","DASHBOARD");
        jsonObject.put("DATA_TYPE", "MACHINE_CONDITION");
        while(rs.next()){
            response.put(rs.getString("NAME"),rs.getString("POWER")+"_"+rs.getInt("VALUE"));
            jsonObject.put(rs.getString("NAME"),rs.getString("POWER")+"_"+rs.getInt("VALUE"));
            sendOrderToMachine(rs.getString("NAME"),rs.getString("POWER"),rs.getInt("VALUE"));

//            for (Custom_Server c: clients) {
//
//            }
        }
        sendToUser(response);
        sendToUser(jsonObject);

    }
    void receiveActuatorData(JSONObject jo) throws Exception {
        switch(jo.getString("MESSAGE_TYPE")) {
            case "INIT":
                init(jo);
                break;
            case "ALIVE":
                break;
            default :
                break;
        }
    }

    void handleMeasureData(JSONObject jo) throws Exception {
        Statement stmt = dbAccessor.con.createStatement();
        //String sql = "SELECT count(*) FROM USER WHERE "+"USERNAME = '"+id+"' AND PASSWORD = '"+password+"'";
        String sql = "UPDATE MACHINE SET VALUE='"+jo.getString("VALUE")+"' WHERE NAME='"+jo.getString("NAME")+"'";
        stmt.executeUpdate(sql);
        JSONObject jsonObject=new JSONObject();
        jsonObject.put("SENDER_TYPE", "SERVER");
        jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
        jsonObject.put("FORM_TYPE","DASHBOARD");
        jsonObject.put("DATA_TYPE", jo.getString("NAME"));
        jsonObject.put("VALUE",jo.getString("VALUE"));
        sendToUser(jsonObject);
    }
    void receiveSensorData(JSONObject jo) throws Exception {
        switch (jo.getString("MESSAGE_TYPE")){
            case "INIT":
                init(jo);
                break;
            case "MEASURE_DATA":
                handleMeasureData(jo);
                break;
            default:
                break;
        }
    }
    void handleResultData(JSONObject jo) throws Exception {
        Statement stmt = dbAccessor.con.createStatement();
        String sql;
        ResultSet rs;
        JSONObject jsonObject;
        switch(jo.getString("NAME")){
            case "CHECK":
                //String sql = "SELECT count(*) FROM USER WHERE "+"USERNAME = '"+id+"' AND PASSWORD = '"+password+"'";
                sql = "UPDATE ORE_USED SET AMOUNT=AMOUNT+1 WHERE MATERIAL_NAME='"+jo.getString("MATERIAL_NAME")+"'";
                stmt.executeUpdate(sql);
                sql = "UPDATE ORE SET AMOUNT=AMOUNT-1 WHERE MATERIAL_NAME='"+jo.getString("MATERIAL_NAME")+"'";
                stmt.executeUpdate(sql);
                sql = "SELECT AMOUNT FROM ORE WHERE MATERIAL_NAME='"+jo.getString("MATERIAL_NAME")+"'";
                rs=stmt.executeQuery(sql);
                int amount=0,used_amount=0;
                while(rs.next()){
                    amount=rs.getInt(1);
                }
                sql = "SELECT AMOUNT FROM ORE_USED WHERE MATERIAL_NAME='"+jo.getString("MATERIAL_NAME")+"'";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    used_amount=rs.getInt(1);
                }
                jsonObject=new JSONObject();
                jsonObject.put("SENDER_TYPE", "SERVER");
                jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject.put("FORM_TYPE","DASHBOARD");
                jsonObject.put("DATA_TYPE", "VALUE");
                jsonObject.put(jo.getString("MATERIAL_NAME"),amount);
                jsonObject.put("USED_"+jo.getString("MATERIAL_NAME"),used_amount);
                jsonObject.put("PROCESS_RATE",jo.getInt("STACK")*20);
                sql = "UPDATE PROCESS SET RATE="+jo.getInt("STACK")*20;
                stmt.executeUpdate(sql);
                sendToUser(jsonObject);
                jsonObject=new JSONObject();
                jsonObject.put("SENDER_TYPE", "SERVER");
                jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject.put("FORM_TYPE","ORE");
                jsonObject.put(jo.getString("MATERIAL_NAME"), amount);
                sendToUser(jsonObject);
                break;
            case "SPLIT":
                sql = "UPDATE ORE_PROCESSED SET "+jo.getString("RESULT")+"="+jo.getString("RESULT")+"+1";
                stmt.executeUpdate(sql);
                sql = "SELECT TERM,"+jo.getString("RESULT")+" FROM ORE_PROCESSED";
                rs=stmt.executeQuery(sql);
                jsonObject=new JSONObject();
                jsonObject.put("SENDER_TYPE", "SERVER");
                jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject.put("FORM_TYPE","DASHBOARD");
                jsonObject.put("DATA_TYPE", "VALUE");
                int processed_amount=0;
                while(rs.next()){
                    processed_amount=rs.getInt(2);
                    jsonObject.put(rs.getString(1)+"_"+jo.getString("RESULT"),processed_amount);
                }
                sql = "SELECT * FROM ORE_PROCESSED";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    processed_amount=rs.getInt(2)+rs.getInt(3);
                    jsonObject.put(rs.getString(1)+"_TOTAL",processed_amount);
                }
                sendToUser(jsonObject);
                //정상품 불량품 db 반영, dashboard 변화 주기
                break;
        }

    }
    void receiveCompoundData(JSONObject jo) throws Exception{
        switch (jo.getString("MESSAGE_TYPE")){
            case "INIT":
                init(jo);
                break;
            case "RESULT_DATA":
                handleResultData(jo);
                break;
            default:
                break;
        }
    }
    public static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch(NumberFormatException e) {
            return false;
        }
    }
    void sendSensorDataToClient() {}
    void sendLoginResponseToClient() {}
    void sendActuatorDataToClient() {}
    void sendOrderToMachine(String target,String power,int value) throws Exception {
        JSONObject order_data=new JSONObject();
        order_data.put("SENDER_TYPE","SERVER");
        order_data.put("MESSAGE_TYPE","STATUS");
        order_data.put("POWER",power);
        if (target.equals("HEATER")){
            order_data.put("VALUE",value);
        }
        else{
            switch (value) {
                case 0:
                    order_data.put("VALUE", "ZERO");
                    break;
                case 1:
                    order_data.put("VALUE", "ONE");
                    break;
                case 2:
                    order_data.put("VALUE", "TWO");
                    break;
            }
        }
        for(Custom_Server c : clients){
            if(c.type.equals("ACTUATOR")&&c.name.equals(target)) c.send(order_data.toString());
        }
    }
    public static void main(String[] args) {
        if (args.length!=0) {
            if (args[0].equals("SecurityModule") && args[1].equals("secomdalcom")) SecurityMode = true;
        }// TODO Auto-generated method stub
        int socket = 8000;
        try {
            ServerSocket ss = new ServerSocket(socket);
            System.out.println(StringColor.ANSI_BLUE+
                    "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓\n" +
                    "┃  ███████╗██████╗                                                      ┃\n" +
                    "┃  ██╔════╝██╔══██╗                                                     ┃\n" +
                    "┃  ███████╗██║  ██║                                                     ┃\n" +
                    "┃  ╚════██║██║  ██║                                                     ┃\n" +
                    "┃  ███████║██████╔╝   ███████╗███████╗██████╗ ██╗   ██╗███████╗██████╗  ┃\n" +
                    "┃  ╚══════╝╚═════╝    ██╔════╝██╔════╝██╔══██╗██║   ██║██╔════╝██╔══██╗ ┃\n" +
                    "┃                     ███████╗█████╗  ██████╔╝██║   ██║█████╗  ██████╔╝ ┃\n" +
                    "┃                     ╚════██║██╔══╝  ██╔══██╗╚██╗ ██╔╝██╔══╝  ██╔══██╗ ┃\n" +
                    "┃                     ███████║███████╗██║  ██║ ╚████╔╝ ███████╗██║  ██║ ┃\n" +
                    "┃                     ╚══════╝╚══════╝╚═╝  ╚═╝  ╚═══╝  ╚══════╝╚═╝  ╚═╝ ┃\n" +
                            "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"+StringColor.ANSI_RESET);
            dbAccessor=new DBAccessor();
            if(SecurityMode) securityModule=new SDSecurity(dbAccessor);
            dbAccessor.userConnectDataReset();
            while (true) {
                Socket user = ss.accept();
                Thread serverThread = new Custom_Server(user);
                serverThread.start();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
interface SecurityModule {
    public void login_s(Custom_Server s,JSONObject jo) throws Exception;
    public boolean validateToken(String jwt) throws Exception;
    public String createToken(String username) throws Exception;
    public JSONObject executeSecurityProcess(Custom_Server c,String src) throws Exception;
    public String encryptData(String src) throws Exception;
    public String decryptData(String src) throws Exception;
}
class DBAccessor{
    static String driver = "org.mariadb.jdbc.Driver";
    static Connection con;
    DBAccessor() {
    try {    Class.forName(driver);
        con = DriverManager.getConnection(
                "jdbc:mariadb://192.168.100.3:3306/posco",
                "root",
                "root");

        if( con != null ) {
            System.out.println(StringColor.ANSI_GREEN+"DB 접속 성공"+StringColor.ANSI_RESET);
        }

    } catch (ClassNotFoundException e) {
        System.out.println(StringColor.ANSI_RED+"드라이버 로드 실패"+StringColor.ANSI_RESET);
    } catch (SQLException e) {
        System.out.println(StringColor.ANSI_RED+"DB 접속 실패"+StringColor.ANSI_RESET);
        e.printStackTrace();
    }
    }
    public void saveLog(String ip,String name,String c_type,String log_lv,String log_information,String activity) throws Exception {
        System.out.print(StringColor.ANSI_YELLOW+"처리내역 로그 저장...\t"+StringColor.ANSI_RESET);
        String sql="INSERT INTO LOG (ACCESS_IP,CLIENT_TYPE,LOG_LEVEL,INFORMATION,ACTIVITY) VALUES ('"+ip+"','"+c_type+"','"+log_lv+"','"+log_information+"','"+activity+"')";
        Statement stmt= con.createStatement();
        stmt.executeUpdate(sql);
        JSONObject jsonObject=new JSONObject();
        jsonObject.put("SENDER_TYPE", "SERVER");
        jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
        jsonObject.put("FORM_TYPE","MANAGE");
        jsonObject.put("DATA_TYPE", "LOG");
        sql="SELECT * FROM LOG ORDER BY IDX DESC LIMIT 1";
        ResultSet rs=stmt.executeQuery(sql);
        while(rs.next()){
            jsonObject.put("DATA",rs.getString("TIMESTAMP")+"|["+rs.getString("ACCESS_IP")+"] " +rs.getString("CLIENT_TYPE")+" "+rs.getString("LOG_LEVEL")+" "+rs.getString("INFORMATION")+" "+rs.getString("ACTIVITY"));
        }
        Custom_Server.sendToUser(jsonObject);
        System.out.println(StringColor.ANSI_GREEN+"완료"+StringColor.ANSI_RESET);
    }
    static void userConnectDataReset() throws SQLException {
        System.out.println(StringColor.ANSI_GREEN+"접속자 정보 초기화"+StringColor.ANSI_RESET);
        String sql="UPDATE USER SET CONNECT_STATUS='DISCONNECT',ACCESSFORM='-'";
        Statement stmts=con.createStatement();
        stmts.executeUpdate(sql);
    }
    ArrayList<String> findLocationsByRole(String role) throws Exception{
        String sql="SELECT LOCATION FROM ACCESS_PERMISSION WHERE ROLE='"+role+"'";
        Statement stmt= con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        ArrayList<String> location=new ArrayList<>();
        while (rs.next()){
            for(String s:rs.getString(1).split(" "))
            { location.add(s);}
        }
        return location;
    }
    String findUserRoleByToken(String token) throws SQLException {
        String sql="SELECT ROLE FROM USER WHERE TOKEN='"+token+"'";
        Statement stmt= con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        String role = "";
        while (rs.next()){
            role=rs.getString(1);
        }
        return role;
    }
    String findUserLocationById(String name) throws SQLException {
        String sql="SELECT ACCESSFORM FROM USER WHERE USERNAME='"+name+"'";
        Statement stmt= con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        String location = "";
        while (rs.next()){
            location=rs.getString(1);
        }
        return location;
    }
    String findMachineStatusByName(String name) throws SQLException {
        String sql="SELECT CONNECT_STATUS FROM MACHINE WHERE NAME='"+name+"'";
        Statement stmt= con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        String result="";
        while(rs.next()) {
            result=rs.getString(1);
        }
        return result;
    }
    String findMachineMacByName(String name) throws SQLException {
        String sql="SELECT MAC_ADDRESS FROM MACHINE WHERE NAME='"+name+"'";
        Statement stmt= con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        String result="";
        while(rs.next()) {
            result=rs.getString(1);
        }
        return result;
    }
    String findUserStatusByName(String name) throws SQLException {
        String sql="SELECT CONNECT_STATUS FROM USER WHERE USERNAME='"+name+"'";
        Statement stmt= con.createStatement();
        ResultSet rs=stmt.executeQuery(sql);
        String result="";
        while(rs.next()) {
            result=rs.getString(1);
        }
        return result;
    }
}
class SDSecurity implements SecurityModule{
    static String key = "SECOMDALCOMSECURITYMODULEDEFAULTKEY";
    DBAccessor dbAccessor;
    SDSecurity(DBAccessor dbAccessor){
        this.dbAccessor=dbAccessor;
        System.out.println(StringColor.ANSI_GREEN_BACKGROUND+"보안모듈실행"+StringColor.ANSI_RESET);
        System.out.println(StringColor.ANSI_GREEN_BACKGROUND+"로그인 보안강화...   완료"+StringColor.ANSI_RESET);
        System.out.println(StringColor.ANSI_GREEN_BACKGROUND+"로그 저장 기능 추가...   완료"+StringColor.ANSI_RESET);
        System.out.println(StringColor.ANSI_GREEN_BACKGROUND+"통신 암호화...   완료"+StringColor.ANSI_RESET);
        System.out.println(StringColor.ANSI_GREEN_BACKGROUND+"인젝션 방지 기능 추가...   완료"+StringColor.ANSI_RESET);

    }

    @Override
    public void login_s(Custom_Server c,JSONObject jo) throws Exception {
        System.out.print(StringColor.ANSI_YELLOW+"["+c.IP+"]에서 로그인 시도...\t"+StringColor.ANSI_RESET);
        int COUNT=0;
        String id = jo.getString("ID");
        String password = jo.getString("PASSWORD");

        Statement stmt = dbAccessor.con.createStatement();
        //String sql = "SELECT count(*) FROM USER WHERE "+"USERNAME = '"+id+"' AND PASSWORD = '"+password+"'";
        String sql = "SELECT count(*) FROM USER WHERE USERNAME = '"+id+"'";
        ResultSet rs = stmt.executeQuery(sql);
        while(rs.next()){
            COUNT = rs.getInt(1);
        }
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "LOGIN_RESPONSE");
        if (COUNT>=1)
        {
            sql = "SELECT PASSWORD,ACCOUNT_STATUS FROM USER WHERE "+"USERNAME = '"+id+"'";
            rs = stmt.executeQuery(sql);
            String PASSWORD="";
            String status="";
            while(rs.next()){
                PASSWORD = rs.getString(1);
                status=rs.getString(2);
            }

            if(status.equals("DISABLED")) {
                System.out.println(StringColor.ANSI_RED+"로그인 실패 (사유:계정이 비활성화 상태입니다.)"+StringColor.ANSI_RESET);
                response.put("RESPONSE_CODE", 4);
                dbAccessor.saveLog(c.IP,id,jo.getString("SENDER_TYPE"),"WARNING","비활성화 계정 로그인 시도","LOGIN");
                c.send(response.toString());
                c.socket.close();
            }
            if(PASSWORD.equals(password)){
                System.out.println(StringColor.ANSI_GREEN+"로그인 성공"+StringColor.ANSI_RESET);
                c.name=id;
                String token=this.createToken(id);
                sql="UPDATE USER SET TOKEN ='"+token+"' WHERE USERNAME ='"+id+"'";
                stmt.executeUpdate(sql);
                response.put("RESPONSE_CODE", 1);
                response.put("TOKEN", token);
                dbAccessor.saveLog(c.IP,id,jo.getString("SENDER_TYPE"),"COMMON","로그인 성공","LOGIN");
                c.send(response.toString());
                c.socket.close();
            }
            else{
                System.out.println(StringColor.ANSI_RED+"로그인 실패 (사유:비밀번호 실패)"+StringColor.ANSI_RESET);
                response.put("RESPONSE_CODE", 3);
                sql="SELECT NUMBER_OF_LOGIN_FAILURES FROM USER WHERE USERNAME='"+id+"'";
                rs=stmt.executeQuery(sql);
                while(rs.next()){
                    if(rs.getInt(1)<4){
                        response.put("NUMBER_OF_FAILURE",rs.getInt(1)+1);
                        sql="UPDATE USER SET NUMBER_OF_LOGIN_FAILURES = NUMBER_OF_LOGIN_FAILURES+1 WHERE USERNAME ='"+id+"'";
                        stmt.executeUpdate(sql);
                    }
                    else{
                        response.put("NUMBER_OF_FAILURE",rs.getInt(1)+1);
                        sql="UPDATE USER SET NUMBER_OF_LOGIN_FAILURES = 0,ACCOUNT_STATUS='DISABLED' WHERE USERNAME ='"+id+"'";
                        stmt.executeUpdate(sql);
                        dbAccessor.saveLog(c.IP,id,jo.getString("SENDER_TYPE"),"ERROR","비밀번호 입력 5회 실패","LOGIN");
                        c.send(response.toString());
                    }
                }
                dbAccessor.saveLog(c.IP,id,jo.getString("SENDER_TYPE"),"WARNING","비밀번호가 틀렸습니다","LOGIN");
                c.send(response.toString());
                c.socket.close();
            }

        }
        else {
            System.out.println(StringColor.ANSI_RED+"로그인 실패 (사유:계정이 존재하지 않음)"+StringColor.ANSI_RESET);
            response.put("RESPONSE_CODE", 2);
            dbAccessor.saveLog(c.IP,id,jo.getString("SENDER_TYPE"),"WARNING","존재하지 않는 계정입니다","LOGIN");
            c.send(response.toString());
            c.socket.close();
        }
    }
    @Override
    public String createToken(String username) throws Exception
    {
        System.out.print(StringColor.ANSI_YELLOW+"토큰 생성...\t"+StringColor.ANSI_RESET);
        Map<String, Object> headers = new HashMap<>();
        headers.put("typ", "JWT");
        headers.put("alg", "HS256");

        Map<String, Object> payloads = new HashMap<>();
        Long expiredTime = 1000*60*60*24l; // 만료기간 1일
        Date now = new Date();
        now.setTime(now.getTime() + expiredTime);
        payloads.put("exp", now);
        payloads.put("data", username);

        String jwt = Jwts.builder()
                .setHeader(headers)
                .setClaims(payloads)
                .signWith(SignatureAlgorithm.HS256, key.getBytes())
                .compact();
        System.out.println(StringColor.ANSI_GREEN+"생성 완료"+StringColor.ANSI_RESET);
        //System.out.println(jwt);
        return jwt;
    }
    @Override
    public boolean validateToken(String jwtTokenString) throws Exception {
        boolean result;
        System.out.print(StringColor.ANSI_YELLOW+"토큰 유효성 체크...\t"+StringColor.ANSI_RESET);
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(key.getBytes())
                    .parseClaimsJws(jwtTokenString)
                    .getBody();
            Date expiration = claims.get("exp", Date.class);
            result=expiration.after(new Date());
            System.out.println((result?StringColor.ANSI_GREEN+"완료":StringColor.ANSI_RED+"실패")+StringColor.ANSI_RESET);
            return result;
        }catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public String trimSpace(String src){
        System.out.print(StringColor.ANSI_YELLOW+"데이터 공백제거...\t"+StringColor.ANSI_RESET);
        String result=src.replaceAll(" ","");
        System.out.println(StringColor.ANSI_GREEN+"완료"+StringColor.ANSI_RESET);
        return result;
    }
    public boolean hasForbiddenCharacters(String src){
        System.out.print(StringColor.ANSI_YELLOW+"문자 유효성 체크...\t"+StringColor.ANSI_RESET);
        JSONObject jo=new JSONObject(src);
        Iterator x = jo.keys();
        while (x.hasNext()){
            String key = (String) x.next();
            if (jo.get(key) instanceof Integer) {
                continue;
            }
            if (jo.get(key) instanceof JSONObject){
                continue;
            }
            if(jo.getString(key).contains("\"")||jo.getString(key).contains("'")){System.out.println(StringColor.ANSI_RED+"제한된 문자 발견"+StringColor.ANSI_RESET); return true;}
        }
        System.out.println(StringColor.ANSI_GREEN+"문자 이상 없음"+StringColor.ANSI_RESET);
        return false;
    }

    @Override
    public JSONObject executeSecurityProcess(Custom_Server c,String src) throws Exception{
//        System.out.println("원본 데이터 :   "+src);
        src=decryptData(src);
        src=trimSpace(src);
        JSONObject jo=new JSONObject(src);
        if(hasForbiddenCharacters(jo.toString())){
            dbAccessor.saveLog(c.IP,c.name,jo.getString("SENDER_TYPE"),"WARNING","사용 금지된 문자 포함 오류",jo.getString("MESSAGE_TYPE"));
            throw new Exception();
        }
        if(jo.getString("SENDER_TYPE").equals("SENSOR")||jo.getString("SENDER_TYPE").equals("ACTUATOR")||jo.getString("SENDER_TYPE").equals("COMPOUND")){
            if(jo.getString("MESSAGE_TYPE").equals("INIT")){
                if(dbAccessor.findMachineMacByName(jo.getString("NAME")).equals("00:00:00:00:00:00")){
                    String sql="UPDATE MACHINE SET MAC_ADDRESS='"+jo.getString("MAC_ADDRESS")+"' WHERE NAME='"+jo.getString("NAME")+"'";
                    Statement stmt= dbAccessor.con.createStatement();
                    stmt.executeUpdate(sql);
                }
            }
            else{
                System.out.println(jo.getString("MAC_ADDRESS"));
                System.out.println(dbAccessor.findMachineMacByName(c.name));
                if(!jo.getString("MAC_ADDRESS").equals(dbAccessor.findMachineMacByName(c.name))){
                    System.out.println(StringColor.ANSI_RED+"등록되지 않은 기기입니다."+StringColor.ANSI_RESET);
                    dbAccessor.saveLog(c.IP,c.name,jo.getString("SENDER_TYPE"),"WARNING","미인증기기 오류",jo.getString("MESSAGE_TYPE"));
                    throw new Exception();
                }
            }
        }
        if(jo.getString("SENDER_TYPE").equals("USER")&&!jo.getString("MESSAGE_TYPE").equals("LOGIN_REQUEST")){
        if (!validateToken(jo.getString("TOKEN"))) {
            System.out.println(StringColor.ANSI_RED+"토큰 유효성 오류"+StringColor.ANSI_RESET);
            dbAccessor.saveLog(c.IP,c.name,jo.getString("SENDER_TYPE"),"WARNING","토큰 유효성 오류",jo.getString("MESSAGE_TYPE"));
            throw new Exception();
            }
            if(!jo.getString("MESSAGE_TYPE").equals("INIT")&&!jo.getString("MESSAGE_TYPE").equals("ALIVE")){
                boolean validateRole=false;
                System.out.print(StringColor.ANSI_YELLOW+"접근 권한 체크...\t"+StringColor.ANSI_RESET);
                for (String location:dbAccessor.findLocationsByRole(dbAccessor.findUserRoleByToken(jo.getString("TOKEN")))) {
                    if(location.equals(jo.getString("FORM_TYPE"))){
                        validateRole=true;
                    }
                }
                if(!validateRole) {
                    System.out.println(StringColor.ANSI_RED+"접근 권한 오류"+StringColor.ANSI_RESET);
                    dbAccessor.saveLog(c.IP,c.name,jo.getString("SENDER_TYPE"),"WARNING","권한 오류",jo.getString("MESSAGE_TYPE"));
                    throw new Exception();
                }
                System.out.println(StringColor.ANSI_GREEN+"접근 권한 이상 없음"+StringColor.ANSI_RESET);
            }
        }
        return jo;
    }
    @Override
    public String encryptData(String src) throws Exception {
        byte[] encodedBytes = Base64.getEncoder().encode(src.getBytes("UTF-8"));
        return new String(encodedBytes,"UTF-8");
    }

    @Override
    public String decryptData(String src) throws Exception{
        System.out.print(StringColor.ANSI_YELLOW+"암호화 데이터 복호화 시작...\t"+StringColor.ANSI_RESET);
        byte[] decodedBytes = Base64.getDecoder().decode(src.getBytes("UTF-8"));
        System.out.println(StringColor.ANSI_GREEN+"완료"+StringColor.ANSI_RESET);
//        System.out.println("BASE64 복호화 결과: "+new String(decodedBytes,"UTF-8"));
        return new String(decodedBytes,"UTF-8");
    }
}

class StringColor {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_RED_BACKGROUND = "\u001B[41m";
    public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
    public static final String ANSI_YELLOW_BACKGROUND = "\u001B[43m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_PURPLE_BACKGROUND = "\u001B[45m";
    public static final String ANSI_CYAN_BACKGROUND = "\u001B[46m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";

}