# Hệ Thống Microservices - Security & API Gateway (Session 13)

Dự án triển khai kiến trúc bảo mật Microservices hoàn chỉnh gồm 3 service:
1. **`identity-service` (Port 8080)**: "Phòng quản lý nhân khẩu", xử lý đăng ký tài khoản (mã hóa BCrypt lưu PostgreSQL), đăng nhập Stateless, cấp cặp bài trùng Access Token (JWT) & Refresh Token (UUID lưu DB), làm mới Access Token (`/refreshtoken`).
2. **`gateway-service` (Port 8088)**: "Cổng kiểm soát an ninh" (Spring Cloud Gateway), định tuyến request tới các microservices phía sau. Chặn các request gửi tới `/api/courses/**` bằng **Global Filter**, dùng chung Secret Key với `identity-service` để giải mã JWT, bắt lỗi 401 Unauthorized nếu thiếu/hết hạn/sai chữ ký, và trích xuất `role` gắn vào header `X-User-Role` trước khi forward.
3. **`course-service` (Port 8082)**: Microservice tài nguyên nghiệp vụ (Resource Service), cung cấp API `/api/courses/**` nhận `X-User-Role` từ Gateway để xử lý nghiệp vụ.

---

## 1. Cấu Trúc Tổng Thể Microservices

```
ex_ss13_micro/
├── .gitignore                      # Chỉ commit source code & cấu hình cần thiết
├── docker-compose.yml              # Khởi chạy PostgreSQL (identity_db) độc lập
├── settings.gradle                 # Quản lý 3 module services
├── gradlew / gradlew.bat           # Gradle Wrapper
├── requests.http                   # File test API sẵn sàng cho VS Code / IntelliJ / Postman
├── README.md                       # Tài liệu hướng dẫn chi tiết
├── identity-service/               # [Port 8080] Auth, BCrypt, PostgreSQL, JWT & Refresh Token
├── gateway-service/                # [Port 8088] Spring Cloud Gateway, Global Filter, JWT verify & header mutation
└── course-service/                 # [Port 8082] Resource Service nhận forward & header X-User-Role
```

---

## 2. Chi Tiết Các Services

### 2.1. Gateway Service (`gateway-service` - Port 8088)
- **Database**: KHÔNG.
- **Dependencies**:
  - `org.springframework.cloud:spring-cloud-starter-gateway`
  - `io.jsonwebtoken:jjwt-api:0.11.5` (kèm `jjwt-impl` và `jjwt-jackson`)
- **Gateway Routing (`application.yml`)**:
  ```yaml
  spring:
    cloud:
      gateway:
        routes:
          - id: course-service
            uri: http://localhost:8082
            predicates:
              - Path=/api/courses/**
          - id: identity-service
            uri: http://localhost:8080
            predicates:
              - Path=/api/auth/**
  ```
- **Global Filter (`JwtAuthenticationFilter.java`)**:
  - Chặn các request có path `/api/courses/**`.
  - Trích xuất token từ header `Authorization: Bearer <token>`.
  - Giải mã bằng Secret Key dùng chung với `identity-service`.
  - Nếu token thiếu, hết hạn (`ExpiredJwtException`) hoặc sai chữ ký (`SignatureException`): Trả về HTTP **`401 Unauthorized`** ngay lập tức.
  - Nếu token hợp lệ: Trích xuất `role` từ Payload, mutate request gắn thêm header **`X-User-Role`** rồi forward xuống `course-service`.

### 2.2. Course Service (`course-service` - Port 8082)
- **Port**: `8082`.
- **API Endpoint**: `GET /api/courses` & `GET /api/courses/{id}`.
- Nhận header `X-User-Role` được chuyển tiếp từ Gateway và trả về danh sách khóa học kèm thông tin quyền hạn truy cập.

### 2.3. Identity Service (`identity-service` - Port 8080)
- **Port**: `8080`.
- **Database**: PostgreSQL (`identity_db`).
- **Các Endpoint**:
  - `POST /api/auth/register`: Đăng ký tài khoản (lưu BCrypt hash).
  - `POST /api/auth/login`: Đăng nhập, trả về cặp `accessToken` (15 phút) và `refreshToken` (7 ngày).
  - `POST /api/auth/refreshtoken`: Dùng `refreshToken` lấy `accessToken` mới.
  - `GET /api/auth/test-token?username={name}`: Cấp token test.

---

## 3. Cấu Hình Khóa Bí Mật Dùng Chung (Shared Secret Key)

Cả `identity-service` và `gateway-service` cùng sử dụng chung Secret Key 256-bit:
```yaml
app:
  jwt:
    secret: "vs6K5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2z"
```

---

## 4. Hướng Dẫn Khởi Chạy & Kiểm Thử

### 4.1. Khởi chạy 3 Services
```powershell
# Chạy Identity Service (Terminal 1)
.\gradlew.bat :identity-service:bootRun

# Chạy Course Service (Terminal 2)
.\gradlew.bat :course-service:bootRun

# Chạy Gateway Service (Terminal 3)
.\gradlew.bat :gateway-service:bootRun
```

### 4.2. Chạy toàn bộ Test tự động (100% PASS)
```powershell
.\gradlew.bat test
```

### 4.3. Kịch bản kiểm thử với Postman / `requests.http`

#### Bước 1: Đăng nhập lấy Access Token
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "user_demo",
  "password": "Password123@"
}
```
Nhận chuỗi `accessToken`.

#### Bước 2: Gọi Gateway KHÔNG đính kèm Access Token (Kỳ vọng 401 Bị chặn)
```http
GET http://localhost:8088/api/courses
```
**Phản hồi (401 Unauthorized)**:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing Authorization Header"
}
```

#### Bước 3: Gọi Gateway với Token sai / hết hạn (Kỳ vọng 401 Bị chặn)
```http
GET http://localhost:8088/api/courses
Authorization: Bearer invalid.token.signature12345
```
**Phản hồi (401 Unauthorized)**:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid token signature"
}
```

#### Bước 4: Gọi Gateway với Access Token hợp lệ (Kỳ vọng 200 OK & Header X-User-Role)
```http
GET http://localhost:8088/api/courses
Authorization: Bearer <dán accessToken từ Bước 1>
```
**Phản hồi (200 OK)**:
```json
{
  "roleReceivedInCourseService": "USER",
  "total": 3,
  "courses": [
    {
      "id": 1,
      "title": "Spring Boot & Microservices Masterclass",
      "instructor": "Rikkei Academy",
      "durationHours": 40,
      "userRole": "USER"
    },
    ...
  ]
}
```
=> Gateway đã giải mã thành công JWT, lấy ra `role: USER` và gắn vào header `X-User-Role` khi forward request xuống Course Service!
