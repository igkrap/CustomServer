import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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


public class Custom_Server extends Thread {
    static ArrayList<Custom_Server> clients = new ArrayList<Custom_Server>();
    static String key = "AVDSDCSDCSFBSFBDFASSDFSDFSDFSDDDF";
    Socket socket;
    String type="",name="";
    static String driver = "org.mariadb.jdbc.Driver";
    static Connection con;
    Statement stmt;
    ResultSet rs;

    public Custom_Server(Socket socket) throws Exception {
        this.socket = socket;
        clients.add(this);
        System.out.println("접속자 수:"+clients.size());
        this.socket.setSoTimeout(15000);

    }
    @Override
    public void run() {
        try {
            while (true) {
                String s, response = "";
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8")); // 읽기
                if ((s = reader.readLine()) != null) {
                    System.out.println("받은 메시지:"+s);
//                    for (Custom_Server c: clients) {
//                        c.send(s);
//                    }
                    JSONObject jObject = new JSONObject(s);
//                    System.out.println(jObject.getString("SENDER_TYPE")+" "+jObject.getString("MESSAGE_TYPE")+" "+jObject.getString("NAME"));
                    switch (jObject.getString("SENDER_TYPE")) {
                        case "USER":
                            this.receiveUserRequest(jObject);
                            break;
                        case "SENSOR":
                            this.receiveSensorData(jObject);
                            break;
                        case "ACTUATOR":
                            this.receiveActuatorData(jObject);
                            break;
                        default:
                            throw new Exception("SENDER_TYPE_ERROR");

                    }
                }
            }
            }
     catch (Exception e) {
            System.out.println(this.name+"소켓연결이 끊겼습니다.");
            // TODO Auto-generated catch block
            try {
                e.printStackTrace();
                socket.close();
                clients.remove(this);
                System.out.println("남은 숫자:"+clients.size());
                JSONObject jsonObject=new JSONObject();
                jsonObject.put("SENDER_TYPE", "SERVER");
                jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject.put("FORM_TYPE","MANAGE");
                if(this.type.equals("ACTUATOR")||this.type.equals("SENSOR")) {
                    String sql="UPDATE MACHINE SET CONNECT_STATUS='DISCONNECT' WHERE NAME='"+this.name+"'";
                    stmt.executeUpdate(sql);
                    jsonObject.put("DATA_TYPE", "MACHINE");
                    jsonObject.put("CONNECT_STATUS",findMachineStatusByName(this.name));
                }
                else if(this.type.equals("USER")){
                    String sql="UPDATE USER SET CONNECT_STATUS='DISCONNECT',ACCESSFORM='' WHERE USERNAME='"+this.name+"'";
                    stmt.executeUpdate(sql);
                    jsonObject.put("DATA_TYPE","USER");
                    jsonObject.put("CONNECT_STATUS",findUserStatusByName(this.name));
                }
                jsonObject.put("NAME",this.name);

                System.out.println("MANAGE 접속중인 유저에게 :"+jsonObject.toString());
                sendToUser(jsonObject);
            } catch (Exception e2) {

                e2.printStackTrace();
            }

        }
    }
    public static String createToken(String username) throws Exception, Exception
    {
        Map<String, Object> headers = new HashMap<>();
        headers.put("typ", "JWT");
        headers.put("alg", "HS256");

        Map<String, Object> payloads = new HashMap<>();
        Long expiredTime = 1000*60*60*24l; // 만료기간 1일
        Date now = new Date();
        System.out.println("토큰 생성 : " +now);
        now.setTime(now.getTime() + expiredTime);
        System.out.println("토큰 만료 : " +now);
        payloads.put("exp", now);
        payloads.put("data", username);

        String jwt = Jwts.builder()
                .setHeader(headers)
                .setClaims(payloads)
                .signWith(SignatureAlgorithm.HS256, key.getBytes())
                .compact();
        //System.out.println(jwt);
        return jwt;
    }
       public static boolean validateToken(String jwtTokenString) throws InterruptedException {
	   try {
	   Claims claims = Jwts.parser()
	        .setSigningKey(key.getBytes())
	        .parseClaimsJws(jwtTokenString)
	        .getBody();
	   Date expiration = claims.get("exp", Date.class);
	   return expiration.after(new Date());
	   }catch(Exception e) {
	       e.printStackTrace();
		   return false;
	   }
	}
    public static void DBcon() {
        try {
            Class.forName(driver);
            con = DriverManager.getConnection(
                    "jdbc:mariadb://192.168.100.3:3306/posco",
                    "root",
                    "root");

            if( con != null ) {
                System.out.println("DB 접속 성공");
            }

        } catch (ClassNotFoundException e) {
            System.out.println("드라이버 로드 실패");
        } catch (SQLException e) {
            System.out.println("DB 접속 실패");
            e.printStackTrace();
        }
    }
    String findUserLocationById(String name) throws SQLException {
        String sql="SELECT ACCESSFORM FROM USER WHERE USERNAME='"+name+"'";
        rs=stmt.executeQuery(sql);
        String location = "";
        while (rs.next()){
            location=rs.getString(1);
        }
        return location;
    }
    void sendToUser(JSONObject jo) throws Exception {
        for (Custom_Server c :clients){
            if(c.type.equals("USER")) {
                if(jo.getString("FORM_TYPE").equals(findUserLocationById(c.name))){
                    c.send(jo.toString());
                }
            }
        }
    }
    String findMachineStatusByName(String name) throws SQLException {
        String sql="SELECT CONNECT_STATUS FROM MACHINE WHERE NAME='"+name+"'";
        rs=stmt.executeQuery(sql);
        String result="";
        while(rs.next()) {
            result=rs.getString(1);
        }
        return result;
    }
    String findMachineMacByName(String name) throws SQLException {
        String sql="SELECT MAC_ADDRESS FROM MACHINE WHERE NAME='"+name+"'";
        rs=stmt.executeQuery(sql);
        String result="";
        while(rs.next()) {
            result=rs.getString(1);
        }
        return result;
    }
    String findUserStatusByName(String name) throws SQLException {
        String sql="SELECT CONNECT_STATUS FROM USER WHERE USERNAME='"+name+"'";
        rs=stmt.executeQuery(sql);
        String result="";
        while(rs.next()) {
            result=rs.getString(1);
        }
        return result;
    }
    void init(JSONObject jo) throws Exception {
        this.type=jo.getString("SENDER_TYPE");
        this.name=jo.getString("NAME");
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "INIT_OK");
        send(response.toString());
        if(this.type.equals("ACTUATOR")||this.type.equals("SENSOR"))
        {
            if(jo.getString("MAC_ADDRESS").equals(findMachineMacByName(this.name))){
                String sql="UPDATE MACHINE SET CONNECT_STATUS='CONNECT' WHERE NAME='"+this.name+"'";
                stmt.executeUpdate(sql);
                JSONObject jsonObject=new JSONObject();
                jsonObject.put("SENDER_TYPE", "SERVER");
                jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
                jsonObject.put("FORM_TYPE","MANAGE");
                jsonObject.put("DATA_TYPE", "MACHINE");
                jsonObject.put("NAME",this.name);
                jsonObject.put("CONNECT_STATUS",findMachineStatusByName(this.name));
                sendToUser(jsonObject);
            }
        }
        else{
            String sql="UPDATE USER SET CONNECT_STATUS='CONNECT' WHERE USERNAME='"+this.name+"'";
            stmt.executeUpdate(sql);
            JSONObject jsonObject=new JSONObject();
            jsonObject.put("SENDER_TYPE", "SERVER");
            jsonObject.put("MESSAGE_TYPE", "FORM_DATA");
            jsonObject.put("FORM_TYPE","MANAGE");
            jsonObject.put("DATA_TYPE", "USER");
            jsonObject.put("NAME",this.name);
            jsonObject.put("CONNECT_STATUS",findUserStatusByName(this.name));
            sendToUser(jsonObject);
        }
//            String sql="SELECT * FROM MACHINE";
//            stmt=con.createStatement();
//            rs=stmt.executeQuery(sql);
//            while(rs.next()){
//                if SENDER_TYPErs.getString(4);
//            }
        System.out.println(this.name);

    }

    void send(String data) throws Exception {
        if(socket.isClosed()) return;
        System.out.println(this.name + "에게 :" + data);
        OutputStream out = socket.getOutputStream(); // 쓰기
        OutputStreamWriter osw1 = new OutputStreamWriter(out, "utf-8");
        PrintWriter writer = new PrintWriter(osw1, true); //
        writer.println(data);
    }
    void orderOre(JSONObject jo) throws Exception {
        String amount=jo.getString("COAL");
        String sql= "UPDATE ORE SET AMOUNT = AMOUNT+"+amount+" WHERE ID ="+2;
        stmt=con.createStatement();
        stmt.executeUpdate(sql);
        amount=jo.getString("IRON");
        sql="UPDATE ORE SET AMOUNT = AMOUNT+"+amount+" WHERE ID ="+1;
        stmt=con.createStatement();
        stmt.executeUpdate(sql);
        multiCastOreData();
    }

    void chooseCountryResponse(JSONObject jo) throws Exception {
        String country=jo.getString("COUNTRY");
        String sql="SELECT * FROM ORE_PRICE_TREND WHERE COUNTRY='"+country+"'";
        stmt = con.createStatement();
        rs=stmt.executeQuery(sql);
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
        stmt = con.createStatement();
        rs=stmt.executeQuery(sql);
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
    void broadCast(){}
    void login(JSONObject jo) throws Exception {
        int COUNT=0;
        String id = jo.getString("ID");
        String password = jo.getString("PASSWORD");
        stmt = con.createStatement();
        String sql = "SELECT count(*) FROM USER WHERE "+"USERNAME = '"+id+"' AND PASSWORD = '"+password+"'";
        rs = stmt.executeQuery(sql);
        while(rs.next()){
            COUNT = rs.getInt(1);
        }
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "LOGIN_REPONSE");
        if (COUNT>=1)
        {
            System.out.println("성공");
            String token=createToken(id);
            sql="UPDATE USER SET token ='"+token+"' WHERE USERNAME ='"+id+"'";
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
        String sql="UPDATE USER SET ACCESSFORM='"+AccessForm+"' WHERE USERNAME ='"+this.name+"'";
        stmt.executeUpdate(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "FORM_DATA");
        response.put("FORM_TYPE", AccessForm);
        response.put("DATA_TYPE","INITIALIZATION");
        switch (AccessForm){
            case "DASHBOARD":
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
                System.out.println(response.toString());
                //response={"SENDER_TYPE":"SERVER","MESSAGE_TYPE":"FORM_DATA","FORM_TYPE":"ORE","AUI":... , "IRON_AMOUNT":...,"COAL_AMOUNT":...}
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
//                sql="SELECT USERNAME,CONNECT_STATUS FROM USER";
//                rs=stmt.executeQuery(sql);
//                while(rs.next()){
//                    JSONObject jsonObject=new JSONObject();
//                    jsonObject.put("NAME",rs.getString(1));
//                    jsonObject.put("CONNECT_STATUS",rs.getString(2));
//                    userArray.put(jsonObject);
//                }
                response.put("MACHINE",machineArray);
                response.put("USER",userArray);
                //response.put("LOG",logArray);
                send(response.toString());
            default:
                break;
        }
    }
    String findUserRoleByToken(String token) throws SQLException {
        String sql="SELECT ROLE FROM USER WHERE TOKEN='"+token+"'";
        rs=stmt.executeQuery(sql);
        String role = "";
        while (rs.next()){
            role=rs.getString(1);
        }
        return role;
    }
    void receiveUserRequest(JSONObject jo) throws Exception {
        String Token;
        switch(jo.getString("MESSAGE_TYPE")) {
            case "INIT":
                Token=jo.getString("TOKEN");
                if (!validateToken(Token)) return;
                System.out.println("토큰성공");
                init(jo);
                break;
            case "LOGIN_REQUEST":
                login(jo);
                break;
            case "ENTER_FORM_REQUEST":
                Token=jo.getString("TOKEN");
                if (!validateToken(Token)) return;
                System.out.println("토큰성공");
                enter(jo);
                break;
            case "ORE_ORDER_REQUEST":
                Token=jo.getString("TOKEN");
                if (!validateToken(Token)) return;
                System.out.println("토큰성공");
                orderOre(jo);
                break;
            case "ORE_COUNTRY_REQUEST":
                Token=jo.getString("TOKEN");
                if (!validateToken(Token)) return;
                System.out.println("토큰성공");
                chooseCountryResponse(jo);
                break;
            case "CONTROL_REQUEST":
                Token=jo.getString("TOKEN");
                if (!validateToken(Token)) return;
                if (!findUserRoleByToken(Token).equals("ADMIN")) return;
                System.out.println("토큰성공");
                controlMachine(jo);
                //sendOrderToMachine(jo.getString("TARGET"),jo.getString("ORDER"));
                break;
            case "MANAGE_USER_REQUEST":
                break;
            case "MANAGE_MACHINE_REQUEST":
                break;
            case "ALIVE":
                break;
            default :
                break;
        }
    }
    void controlMachine(JSONObject jo) throws Exception {
        String sql= "UPDATE MACHINE SET POWER= '"+jo.getString("POWER")+"'" +
                ", VALUE=" +jo.getInt("VALUE")+
                " WHERE NAME='"+jo.getString("TARGET")+"'";
        stmt.executeUpdate(sql);
        sql="SELECT * FROM MACHINE WHERE NAME='"+jo.getString("TARGET")+"'";
        rs=stmt.executeQuery(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE","FORM_DATA");
        response.put("FORM_TYPE","CONTROL");
        response.put("DATA_TYPE","CONDITION");
        while(rs.next()){
            System.out.println(rs.getString("NAME")+rs.getString("POWER")+rs.getInt("VALUE"));
            response.put(rs.getString("NAME"),rs.getString("POWER")+"_"+rs.getInt("VALUE"));
            sendOrderToMachine(rs.getString("NAME"),rs.getString("POWER"),rs.getInt("VALUE"));

//            for (Custom_Server c: clients) {
//
//            }
        }
        sendToUser(response);

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
    void handleMeasureData(JSONObject jo) {
        switch (this.name){
            case "TMP_SENSOR":
                break;
            case "CHECK":
                break;
            case "SPLIT":
                break;
            case "GAS_SENSOR":
                break;

        };
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
            if(c.name.equals(target)) c.send(order_data.toString());
        }
    }

    public static void main(String[] args) {
        // TODO Auto-generated method stub
        int socket = 8000;
        try {
            ServerSocket ss = new ServerSocket(socket);
            System.out.println("서버 열림");
            DBcon();
//            Statement stm = con.createStatement();;
//            ResultSet rss;
//            String sql;
//            JSONObject response=new JSONObject();
//            response.put("MESSAGE_TYPE", "ORE_PRICE_DATA");
//            sql = "SELECT * FROM ORE_PRICE_TREND";
//            rss = stm.executeQuery(sql);
//            int dd=0;
//            while(rss.next()){
//                String code=rss.getString(2).substring(0,2)+rss.getString(3).substring(0,1)+rss.getString(4).substring(0,1);
//                response.put(code,rss.getInt(5));
//            }
//            sql = "SELECT * FROM ORE";
//            rss =stm.executeQuery(sql);
//            while(rss.next()){
//                response.put(rss.getString(2)+"_AMOUNT",rss.getInt(3));
//            }
//            System.out.println(response.toString());

            while (true) {
                Socket user = ss.accept();
                System.out.println("클라이언트 입장 " + user.getRemoteSocketAddress() );
                Thread serverThread = new Custom_Server(user);
                serverThread.start();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}