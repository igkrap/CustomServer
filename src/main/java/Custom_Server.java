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
                    System.out.println(s);
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
                            break;
                    }
                }
            }
            } catch (UnsupportedEncodingException unsupportedEncodingException) {
            unsupportedEncodingException.printStackTrace();
        }
     catch (IOException e) {
            // TODO Auto-generated catch block
            System.out.println("소켓연결이 끊겼습니다.");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
    void sendToTarget(String data) throws Exception {
        for (Custom_Server client : clients) {
            //if (something~){
            //client.send(data);}
        }
    }
    void init(JSONObject jo) throws SQLException {
        this.type=jo.getString("SENDER_TYPE");
        this.name=jo.getString("NAME");
        if(this.type.equals("ACTUATOR")||this.type.equals("SENSOR"))
        {
//            String sql="SELECT * FROM MACHINE";
//            stmt=con.createStatement();
//            rs=stmt.executeQuery(sql);
//            while(rs.next()){
//                rs.getString();
//            }
        }
            System.out.println(this.name);
    }
    void send(String data) throws Exception {
        System.out.println(this.name+"에게 :"+data);
        OutputStream out = socket.getOutputStream(); // 쓰기
        OutputStreamWriter osw1 = new OutputStreamWriter(out, "utf-8");
        PrintWriter writer = new PrintWriter(osw1, true); //
        writer.println(data);
    }
    void orderOre(JSONObject jo) throws Exception {
        int amount=jo.getInt("COAL");
        String sql= "UPDATE ORE SET AMOUNT = AMOUNT+"+amount+" WHERE ID ="+2;
        stmt=con.createStatement();
        stmt.executeUpdate(sql);
        amount=jo.getInt("IRON");
        sql="UPDATE ORE SET AMOUNT = AMOUNT+"+amount+" WHERE ID ="+1;
        broadCastOreData();
    }

    void chooseCountryResponse(JSONObject jo) throws Exception {
        String country=jo.getString("COUNTRY");
        String sql="SELECT * FROM ORE_PRICE_TREND WHERE COUNTRY='"+country+"'";
        stmt = con.createStatement();
        rs=stmt.executeQuery(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE","ORE_COUNTRY_DATA");
        while(rs.next()) {
            String code = rs.getString(3).substring(0, 1) + rs.getString(4).substring(0, 1);
            response.put(code, rs.getInt(5));
        }
        send(response.toString());
    }
    void broadCastOreData() throws Exception {
        String sql="SELECT * FROM ORE";
        stmt = con.createStatement();
        rs=stmt.executeQuery(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE","ORE_AMOUNT_DATA");
        while(rs.next()) {
            response.put(rs.getString(2),rs.getInt(3));
        }
        for (Custom_Server c :clients){
            if(c.type.equals("user")) {
                c.send(response.toString());
            }
        }
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
        System.out.println("토큰성공");
        String Token=jo.getString("TOKEN");
        if (!validateToken(Token)) return;
        String sql="UPDATE USER SET ACCESSFORM='"+AccessForm+"' WHERE TOKEN ='"+Token+"'";
        stmt.executeUpdate(sql);
        JSONObject response=new JSONObject();
        response.put("SENDER_TYPE", "SERVER");
        response.put("MESSAGE_TYPE", "FORM_DATA");
        response.put("FORM_TYPE", AccessForm);
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
                sql= "SELECT * FROM MACHINE";
                rs = stmt.executeQuery(sql);
                while(rs.next()){
                    if(rs.getString(4).equals("THISMONTH")) {
                        String code = rs.getString(2).substring(0, 2) + rs.getString(3).substring(0, 1);
                        response.put(code, rs.getInt(5));
                    }
                }
                break;

            default:
                break;
        }
    }
    void receiveUserRequest(JSONObject jo) throws Exception {
        switch(jo.getString("MESSAGE_TYPE")) {
            case "INIT":
                init(jo);
                break;
            case "LOGIN_REQUEST":
                login(jo);
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
            case "CONTROLL_REQUEST":
                controlMachine(jo);
                //sendOrderToMachine(jo.getString("TARGET"),jo.getString("ORDER"));
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
        stmt=con.createStatement();
        stmt.executeUpdate(sql);
        sql="SELECT * FROM MACHINE WHERE NAME='"+jo.getString("TARGET")+"'";
        stmt=con.createStatement();
        rs=stmt.executeQuery(sql);
        while(rs.next()){
            System.out.println(rs.getString("NAME")+rs.getString("POWER")+rs.getInt("VALUE"));
            sendOrderToMachine(rs.getString("NAME"),rs.getString("POWER"),rs.getInt("VALUE"));
            for (Custom_Server c: clients) {

            }
        }

    }
    void receiveActuatorData(JSONObject jo) throws SQLException {
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
    void receiveSensorData(JSONObject jo) {
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
        order_data.put("MESSAGE_TYPE", "STATUS");
        order_data.put("POWER",power);
        switch (value){
            case 0:
                order_data.put("VALUE","ZERO");
                break;
            case 1:
                order_data.put("VALUE","ONE");
                break;
            case 2:
                order_data.put("VALUE","TWO");
                break;
        }

        for(Custom_Server c : clients){
            System.out.println("sendOrderToMachine: "+c.name);
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
                System.out.println("클라이언트 입장 " + user.getLocalAddress() + " : " + user.getLocalPort());
                Thread serverThread = new Custom_Server(user);
                serverThread.start();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}