# Identity Service - Hệ Thống Microservices (Session 13)

Dự án triển khai **Identity Service** ("Phòng quản lý nhân khẩu" trong hệ thống Microservices), đảm nhiệm toàn bộ quy trình quản lý danh tính và cấp phát token:
1. **Bài 1**: Đăng ký và lưu trữ tài khoản người dùng an toàn với mật khẩu băm BCrypt trong PostgreSQL.
2. **Bài 2**: Phát hành "tấm thẻ" (JWT Token) chứa thông tin `sub` (username) và `role` để người dùng xác thực với các service khác trong hệ thống.
3. **Bài 3**: API Đăng nhập (`POST /api/auth/login`), kiến trúc Stateless (không lưu Session/JSESSIONID), đối soát mật khẩu an toàn bằng `passwordEncoder.matches()` và phản hồi `401 Bad credentials` khi thông tin không khớp.
4. **Cặp bài trùng Access Token & Refresh Token**:
   - **Access Token**: JWT, thời gian sống ngắn (15 phút = 900.000ms), chứa claims (`sub`, `role`, `roles`, `exp`), dùng để gọi API.
   - **Refresh Token**: Chuỗi định danh ngẫu nhiên (UUID), thời gian sống dài (7 ngày = 604.800.000ms), lưu trong bảng `refresh_tokens` trong PostgreSQL, dùng để lấy Access Token mới qua API `/api/auth/refreshtoken`.

---

## 1. Cấu Trúc Dự Án

```
ex_ss13_micro/
├── .gitignore                      # Chỉ commit source code & cấu hình cần thiết
├── docker-compose.yml              # Khởi chạy PostgreSQL (identity_db) độc lập
├── settings.gradle                 # Quản lý module identity-service
├── gradlew / gradlew.bat           # Gradle Wrapper
├── requests.http                   # File test API sẵn sàng cho VS Code / IntelliJ
├── README.md                       # Tài liệu hướng dẫn chi tiết
└── identity-service/
    ├── build.gradle                # Khai báo dependencies Spring Boot, Security, JPA, JJWT, PostgreSQL
    └── src/
        ├── main/
        │   ├── java/com/example/identityservice/
        │   │   ├── IdentityServiceApplication.java
        │   │   ├── config/
        │   │   │   └── SecurityConfig.java         # Stateless Session & permitAll auth endpoints
        │   │   ├── controller/
        │   │   │   └── AuthController.java         # /register, /login, /refreshtoken, /test-token
        │   │   ├── dto/
        │   │   │   ├── RegisterRequest.java        # Request đăng ký
        │   │   │   ├── LoginRequest.java           # Request đăng nhập (username, password)
        │   │   │   ├── LoginResponse.java          # Response đăng nhập (accessToken, refreshToken, tokenType Bearer)
        │   │   │   ├── TokenRefreshRequest.java    # Request làm mới token (refreshToken)
        │   │   │   ├── TokenRefreshResponse.java   # Response làm mới token
        │   │   │   └── UserResponse.java           # Response không chứa password
        │   │   ├── entity/
        │   │   │   ├── User.java                   # Entity User (id UUID, username, password, role)
        │   │   │   └── RefreshToken.java           # Entity RefreshToken (id, token UUID, expiryDate, user_id)
        │   │   ├── exception/
        │   │   │   ├── DuplicateResourceException.java
        │   │   │   ├── ResourceNotFoundException.java
        │   │   │   ├── TokenRefreshException.java
        │   │   │   └── GlobalExceptionHandler.java # Xử lý 401 Bad credentials, 403, 404, 409, 400
        │   │   ├── repository/
        │   │   │   ├── UserRepository.java
        │   │   │   └── RefreshTokenRepository.java
        │   │   ├── service/
        │   │   │   ├── UserService.java
        │   │   │   ├── RefreshTokenService.java
        │   │   │   └── impl/
        │   │   │       ├── UserServiceImpl.java    # Xác thực BCrypt & cấp cặp AccessToken + RefreshToken
        │   │   │       └── RefreshTokenServiceImpl.java # Quản lý vòng đời RefreshToken trong DB
        │   │   └── util/
        │   │       ├── JwtUtil.java                # Tạo & giải mã JWT Token (HS256, claim sub, role/roles)
        │   │       └── JwtUtils.java               # Boilerplate helper cho generateAccessToken
        │   └── resources/
        │       └── application.yml                 # Cấu hình PostgreSQL & TTL token
        └── test/
            └── java/com/example/identityservice/
                ├── IdentityServiceApplicationTests.java
                ├── controller/
                │   ├── AuthControllerTest.java     # Test controller endpoints
                │   └── RegisterIntegrationTest.java# Test End-to-End đầy đủ luồng Register, Login & Refresh
                ├── service/
                │   ├── UserServiceTest.java
                │   └── RefreshTokenServiceTest.java# Test tạo UUID token & kiểm tra hết hạn
                └── util/
                    └── JwtUtilTest.java            # Test sinh token, claim sub, role, TTL 15 phút
```

---

## 2. Cấu Hình Hệ Thống (`application.yml`)

```yaml
server:
  port: 8080

spring:
  application:
    name: identity-service
  datasource:
    url: jdbc:postgresql://localhost:5432/identity_db
    username: postgres
    password: ${POSTGRES_PASSWORD:17111998}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update

app:
  jwt:
    secret: "vs6K5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2z"
    access-token-expiration: 900000 # 15 phút (900.000 ms)
    refresh-token-expiration: 604800000 # 7 ngày (604.800.000 ms)
```

---

## 3. Chi Tiết Triển Khai Kỹ Thuật

### 3.1. Entity `RefreshToken` & Database PostgreSQL
- **Bảng**: `refresh_tokens`
- **Cấu trúc trường**:
  - `id`: VARCHAR(255), Primary Key.
  - `token`: VARCHAR(255), UNIQUE, NOT NULL (chuỗi UUID ngẫu nhiên).
  - `expiry_date`: TIMESTAMP WITH TIME ZONE, NOT NULL (thời điểm hết hạn sau 7 ngày).
  - `user_id`: VARCHAR(255), Foreign Key tham chiếu `users(id)`.

Kiểm tra bằng SQL:
```sql
SELECT * FROM refresh_tokens;
```

### 3.2. Lớp `JwtUtils` (`generateAccessToken(User user)`)
- Tạo chuỗi JWT với thời gian sống 15 phút (900.000ms).
- Ký bảo mật với thuật toán HS256 và khóa 256-bit.
- **Payload**:
  - `sub`: username của người dùng.
  - `role` & `roles`: quyền hạn của người dùng.
  - `iat`: thời điểm khởi tạo.
  - `exp`: thời điểm hết hạn.

### 3.3. Dịch vụ `RefreshTokenService`
- `createRefreshToken(userId)`: Tạo chuỗi `UUID.randomUUID()`, tính toán `expiryDate` theo cấu hình, cập nhật hoặc thêm mới vào bảng `refresh_tokens`.
- `verifyExpiration(token)`: Kiểm tra token có quá hạn so với thời gian hiện tại không; nếu hết hạn thì xóa khỏi DB và ném `TokenRefreshException`.

### 3.4. API Endpoints

| Method | Endpoint | Mô tả | Yêu cầu kết quả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Đăng ký người dùng | HTTP 201 Created (lưu hash BCrypt, trả về user không kèm password) |
| `POST` | `/api/auth/login` | Đăng nhập tài khoản | HTTP 200 OK kèm cả `accessToken` và `refreshToken` (hoặc 401 Unauthorized nếu sai) |
| `POST` | `/api/auth/refreshtoken`| Đổi Access Token mới | HTTP 200 OK kèm `accessToken` mới |
| `GET` | `/api/auth/test-token?username={name}` | Sinh JWT Token trực tiếp | HTTP 200 OK chuỗi JWT (hoặc 404 nếu không thấy user) |

---

## 4. Hướng Dẫn Chạy & Kiểm Thử

### Khởi chạy ứng dụng:
```powershell
.\gradlew.bat :identity-service:bootRun
```

### Chạy toàn bộ Test tự động (100% PASS):
```powershell
.\gradlew.bat :identity-service:test
```

### Kiểm tra bằng `requests.http` hoặc Postman:
1. **Đăng nhập**:
   ```http
   POST http://localhost:8080/api/auth/login
   Content-Type: application/json

   {
     "username": "user_demo",
     "password": "Password123@"
   }
   ```
   **Phản hồi (200 OK)**:
   ```json
   {
     "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
     "refreshToken": "48fe7e32-a5e2-4978-83b3-855bc9bfeec0",
     "tokenType": "Bearer"
   }
   ```

2. **Kiểm tra Access Token trên `jwt.io`**:
   Dán `accessToken` vào [jwt.io](https://jwt.io), kiểm tra Payload có đúng:
   ```json
   {
     "sub": "user_demo",
     "role": "USER",
     "roles": "USER",
     "iat": 1731427200,
     "exp": 1731428100
   }
   ```
