# 📝 Text Annotation Platform

A collaborative web-based platform for text annotation and dataset management, built with **Spring Boot** and **Thymeleaf**.

---

## 🚀 Features

### 📂 Dataset Management
- Upload Excel/CSV files containing text pairs
- Define custom classification categories
- Monitor dataset processing status
- Review and manage uploaded datasets

### 👥 Task Assignment
- Assign annotation tasks to users
- Set deadlines for task completion
- Track task progress and completion
- Distribute workload across multiple annotators

### ✍️ Annotation Interface
- Clean, intuitive UI for annotating text pairs
- Support for multiple classification categories
- Track progress and indicate task completion
- Direct validation of annotations

### 🛠 Admin Controls
- User management (add/edit/delete annotators)
- Dataset oversight and management
- Task supervision and diagnostics
- System monitoring tools

---

## 🧰 Technical Stack

| Layer        | Technology              |
|--------------|--------------------------|
| Backend      | Spring Boot              |
| Frontend     | Thymeleaf, TailwindCSS   |
| Database     | MySQL                    |
| Build Tool   | Maven                    |

---

## ⚙️ Setup Instructions

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- MySQL 8.0+

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/text-annotation-platform.git
cd text-annotation-platform
```

### 2. Configure the Database
Create a new MySQL database and edit the `application.properties` file with your credentials:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/your_db_name
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.servlet.multipart.max-file-size=80MB
spring.servlet.multipart.max-request-size=80MB
```

### 3. Build and Run the Project
```bash
mvn clean install
mvn spring-boot:run
```

### 4. Access the Application
Visit the app at: [http://localhost:8080](http://localhost:8080)

---

## 🗂 Project Structure

```
annotationapp/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/annotation/
│       │       ├── controller/        # MVC controllers
│       │       ├── model/             # JPA entity classes
│       │       ├── repository/        # Spring Data JPA interfaces
│       │       ├── service/           # Business logic layer
│       │       └── config/            # Configuration classes
│       └── resources/
│           ├── templates/             # Thymeleaf templates
│           ├── static/                # CSS, JS, and images
│           └── application.properties # Configuration file
├── pom.xml                           # Maven dependencies
└── README.md                         # This file
```

---

## 🔐 Security Features

- **Role-based Access Control**: ADMIN and USER roles with different permissions
- **Secure Password Handling**: BCrypt encryption for user passwords
- **Protected Admin Endpoints**: Admin-only access to sensitive operations
- **Session Management**: Secure session handling and timeout controls
- **CSRF Protection**: Cross-site request forgery protection enabled

---

## 📁 File Support

| Format | Extensions | Max Size | Features |
|--------|------------|----------|----------|
| Excel  | .xlsx, .xls | 80MB | Automatic sheet detection |
| CSV    | .csv | 80MB | Delimiter auto-detection |

**Validation Features:**
- Automatic format and structure validation
- Error reporting for malformed files
- Preview before final upload
- Batch processing support

---

## 🎯 Usage Guide

### For Administrators
1. **Login** with admin credentials
2. **Upload Dataset**: Navigate to dataset management and upload your files
3. **Create Tasks**: Assign annotation tasks to users with deadlines
4. **Monitor Progress**: Track completion rates and user performance
5. **Manage Users**: Add, edit, or remove annotator accounts

### For Annotators
1. **Login** with your user credentials
2. **View Tasks**: See assigned annotation tasks on your dashboard
3. **Annotate**: Click on tasks to start annotating text pairs
4. **Save Progress**: Your work is automatically saved
5. **Submit**: Mark tasks as complete when finished

---

## 🔧 Development

### Running in Development Mode
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Running Tests
```bash
mvn test
```

### Building for Production
```bash
mvn clean package -Pprod
java -jar target/annotation-platform-1.0.0.jar
```

---

## 🌐 API Endpoints

### Authentication
- `POST /login` - User login
- `POST /logout` - User logout

### Dataset Management
- `GET /admin/datasets` - List all datasets
- `POST /admin/datasets/upload` - Upload new dataset
- `DELETE /admin/datasets/{id}` - Delete dataset

### Task Management
- `GET /tasks` - Get user tasks
- `POST /admin/tasks/assign` - Assign tasks
- `PUT /tasks/{id}/complete` - Mark task complete

### Annotation
- `GET /annotate/{taskId}` - Get annotation interface
- `POST /annotate/{taskId}/save` - Save annotation

---

## 🚨 Troubleshooting

### Common Issues

**Database Connection Error**
```
Solution: Check MySQL service is running and credentials are correct
```

**File Upload Fails**
```
Solution: Ensure file size is under 80MB and format is supported
```

**Login Issues**
```
Solution: Verify user exists and password is correct. Check session timeout.
```

### Logs Location
- Application logs: `logs/application.log`
- Error logs: `logs/error.log`

---

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Code Style
- Follow Java naming conventions
- Use proper indentation (4 spaces)
- Add comments for complex logic
- Write unit tests for new features

---

## 📊 Performance

- **Concurrent Users**: Supports up to 100 simultaneous users
- **File Processing**: Handles files up to 80MB efficiently
- **Database**: Optimized queries with proper indexing
- **Response Time**: Average response time under 200ms

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 Text Annotation Platform

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

## 🙋‍♀️ Support

- **Documentation**: [Wiki](../../wiki)
- **Issues**: [GitHub Issues](../../issues)
- **Discussions**: [GitHub Discussions](../../discussions)
- **Email**: support@annotation-platform.com

---

## 🏆 Acknowledgments

- Spring Boot team for the excellent framework
- Thymeleaf community for template engine support
- TailwindCSS for beautiful styling utilities
- All contributors who helped improve this project

---

**Made with ❤️ by the Text Annotation Platform Team**
