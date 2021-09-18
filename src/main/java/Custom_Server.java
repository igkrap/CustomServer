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
    Socket socket;
    String type;
    static String driver = "org.mariadb.jdbc.Driver";
    static Connection con;
    Statement stmt;
    ResultSet rs;

    public Custom_Server(Socket socket) {
        this.socket = socket;
        clients.add(this);
        System.out.println("접속자 수:"+clients.size());
    }
    @Override
    public void run() {
        try {
            while (true) {
                String s,response="";
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input,"UTF-8")); // 읽기
                if ((s = reader.readLine()) != null) {
                    System.out.println(s);
                    JSONObject jObject = new JSONObject(s);
                    switch(jObject.getString("SENDER_TYPE")) {
                        case "USER": this.receiveUserRequest(jObject); break;
                        case "SENSOR": this.receiveSensorData(jObject); break;
                        case "ACTUATOR": this.receiveActuatorData(jObject); break;
                        default: break;
                    }
                }
            }
        } catch (IOException | SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    public static String createToken(String username) throws Exception, Exception
    {
        String key = "AVDSDCSDCSFBSFBDFASSDFSDFSDFSDDDF";

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
        System.out.println(jwt);
        return jwt;
    }
       public static boolean validateToken(String jwtTokenString) throws InterruptedException {
	   try {
	   Claims claims = Jwts.parser()
	        .setSigningKey("AVDSDCSDCSFBSFBDFASSDFSDFSDFSDDDF".getBytes())
	        .parseClaimsJws(jwtTokenString)
	        .getBody();
	   Date expiration = claims.get("exp", Date.class);
	   System.out.println(expiration);
	   return expiration.before(new Date());
	   }catch(Exception e) {
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
    void sendToTargets(String data) throws Exception {
        for (Custom_Server client : clients) {
            //if (something~){
            //client.send(data);}
        }
    }
    void init(JSONObject jo) {
        this.type=jo.getString("SENDER_TYPE");
    }
    void send(String data) throws Exception {
        OutputStream out = socket.getOutputStream(); // 쓰기
        OutputStreamWriter osw1 = new OutputStreamWriter(out, "utf-8");
        PrintWriter writer = new PrintWriter(osw1, true); //
        writer.println(data);
    }
    void orderRawMaterial(JSONObject jo) throws SQLException {
        int mat_index=jo.getInt("MATERIAL_INDEX");
        int amount=jo.getInt("AMOUNT");
        String sql= "UPDATE raw_material SET amount = amount+"+amount+" WHERE INDEX ="+mat_index;
        stmt=con.createStatement();
        stmt.executeUpdate(sql);
    }
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
    void enter(JSONObject jo){
        switch (jo.getString("FORM_TYPE")){
            case "DASHBOARD":
                break;
            case "ORDER_RAW":
                break;
            case "SCHEDULE":
                break;
            case "MONITORING_CONTROL":
                break;
            case "PRODUCT":
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
            case "ORDER_RAW_REQUEST":
                orderRawMaterial(jo);
                break;

            default : break;
        }
    }

    void receiveActuatorData(JSONObject jo) {}
    void receiveSensorData(JSONObject jo) {}
    void sendSensorDataToClient() {}
    void sendLoginResponseToClient() {}
    void sendActuatorDataToClient() {}
    void sendOrderToMachine() {}

    public static void main(String[] args) {
        // TODO Auto-generated method stub
        int socket = 8000;
        try {
            ServerSocket ss = new ServerSocket(socket);
            System.out.println("서버 열림");
            DBcon();
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