package com.examnest.examnest;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;

import java.util.Map;

import java.security.MessageDigest;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import java.util.UUID;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;

@Controller
public class HomeController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotesRepository notesRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    @Autowired
    private ActivityLogRepository activityLogRepository;
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private DownloadHistoryRepository downloadHistoryRepository;
    
    @Autowired
    private JavaMailSender mailSender;
    
    private void sendPasswordChangeEmail(User user) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("prajnasree78@gmail.com");
        message.setTo(user.getEmail());
        message.setSubject("Your Password Was Changed - ExamNest");
        message.setText("Hello " + user.getUsername() +
                ",\n\nYour password was successfully changed.\n\nIf this wasn't you, contact support immediately.");

        mailSender.send(message);
    }

    private void sendAccountDeletedEmail(User user) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("prajnasree78@gmail.com");
        message.setTo(user.getEmail());
        message.setSubject("Your ExamNest Account Was Deleted");
        message.setText("Hello " + user.getUsername() +
                ",\n\nYour account has been permanently deleted.\n\nIf this was not done by you, contact support.");

        mailSender.send(message);
    }
    
    private void sendVerificationEmail(User user) {

        String verifyURL = "http://localhost:8080/verify?token="
                + user.getVerificationToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("prajnasree78@gmail.com");   // your Brevo verified sender
        message.setTo(user.getEmail());
        message.setSubject("Verify Your ExamNest Account");
        message.setText("Click below link to verify your account:\n" + verifyURL);

        mailSender.send(message);
    }
    
    private void createNotification(String message) {
        Notification notification = new Notification();
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
    
    private Map<String, List<String>> subjectKeywords = Map.of(
    	    "ATCD", List.of("compiler", "parser", "lexer", "syntax", "lexical"),
    	    "DBMS", List.of("database", "sql", "normalization", "transaction"),
    	    "OS", List.of("process", "thread", "deadlock", "scheduling")

    	);
    // =========================
    // Home Page
    // =========================
    @GetMapping("/")
    public String home(HttpSession session,
                       @CookieValue(value = "rememberUser", required = false) String rememberedUsername) {

        // If session already exists
        if (session.getAttribute("loggedUser") != null) {
            User user = (User) session.getAttribute("loggedUser");

            if (user.getRole().equals("ROLE_ADMIN")) {
                return "redirect:/admin";
            } else {
                return "redirect:/dashboard";
            }
        }

        // If cookie exists but session not exists
        if (rememberedUsername != null) {

            User user = userRepository.findByUsername(rememberedUsername);

            if (user != null) {
                session.setAttribute("loggedUser", user);
                
                System.out.println("ROLE FROM DB: " + user.getRole());
                if (user.getRole().equals("ROLE_ADMIN")) {
                    return "redirect:/admin";
                } else {
                    return "redirect:/dashboard";
                }
            }
        }

        return "login";
    }
    @GetMapping("/login")
    public String loginPage(HttpSession session) {

        if (session.getAttribute("loggedUser") != null) {
            User user = (User) session.getAttribute("loggedUser");

            if (user.getRole().equals("ROLE_ADMIN")) {
                return "redirect:/admin";
            } else {
                return "redirect:/dashboard";
            }
        }

        return "login";
    }
    private void logActivity(String username, String action) {
        ActivityLog log = new ActivityLog();
        log.setUsername(username);
        log.setAction(action);
        log.setTimestamp(LocalDateTime.now());
        activityLogRepository.save(log);
    }

    // =========================
    // Login
    // =========================
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String rememberMe,
                        Model model,
                        HttpSession session,
                        jakarta.servlet.http.HttpServletResponse response) {

        User user = userRepository.findByUsername(username);

        if (user == null) {
            model.addAttribute("error", "Invalid Username or Password");
            return "login";
        }

        if (user.isAccountLocked()) {
            model.addAttribute("error", "Account locked. Contact admin.");
            return "login";
        }
        
        if (!user.isEnabled()) {
            model.addAttribute("error", "Please verify your email first!");
            return "login";
        }

        if (passwordEncoder.matches(password, user.getPassword())) {

            user.setFailedAttempts(0);
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            session.setAttribute("loggedUser", user);
            logActivity(user.getUsername(), "Logged in");

            // ✅ Remember Me Logic (Correct Place)
            if (rememberMe != null) {

                jakarta.servlet.http.Cookie cookie =
                        new jakarta.servlet.http.Cookie("rememberUser", user.getUsername());

                cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
                cookie.setPath("/");

                response.addCookie(cookie);
            }

            if (user.getRole().equals("ROLE_ADMIN")) {
                return "redirect:/admin";
            } else {
                return "redirect:/dashboard";
            }

        } else {

            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= 5) {
                user.setAccountLocked(true);
                logActivity(user.getUsername(), "Account locked due to failed attempts");
            }

            userRepository.save(user);

            model.addAttribute("error", "Invalid Username or Password");
            return "login";
        }
    }
    // =========================
    // Register Page
    // =========================
    @GetMapping("/register")
    public String showRegister() {
        return "register";
    }

    // =========================
    // Register
    // =========================
    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           Model model) {

        if (userRepository.findByUsername(username) != null) {
            model.addAttribute("error", "Username already exists");
            return "register";
        }

        if (userRepository.findByEmail(email) != null) {
            model.addAttribute("error", "Email already exists");
            return "register";
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("ROLE_USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setEnabled(false);

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);

        userRepository.save(user);

        sendVerificationEmail(user);

        model.addAttribute("success", "Registered successfully! Check your email.");
        return "register";
    }

    // =========================
    // Dashboard
    // =========================
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String year,
                            @RequestParam(required = false) String branch,
                            Model model,
                            HttpSession session) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        model.addAttribute("username", user.getUsername());
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedBranch", branch);

        List<Notes> notes = Collections.emptyList();

        if (year != null && branch != null) {
            notes = notesRepository.findByYearAndBranch(year, branch);
        }

        model.addAttribute("notes", notes);

        return "dashboard";
    }
    // =========================
    // Logout
    // =========================
    @GetMapping("/logout")
    public String logout(HttpSession session,
                         jakarta.servlet.http.HttpServletResponse response) {

        session.invalidate();

        jakarta.servlet.http.Cookie cookie =
                new jakarta.servlet.http.Cookie("rememberUser", null);

        cookie.setMaxAge(0);
        cookie.setPath("/");

        response.addCookie(cookie);

        return "redirect:/";
    }

    // =========================
    // Upload Page
    // =========================
    
    @GetMapping("/upload")
    public String uploadPage(HttpSession session) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        return "upload";
    }
    
    @PostMapping("/upload")
    public String uploadNotes(@RequestParam String title,
                              @RequestParam String year,
                              @RequestParam String branch,
                              @RequestParam String subject,
                              @RequestParam("file") MultipartFile file,
                              HttpSession session,
                              Model model) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        try {

            if (file.isEmpty()) {
                model.addAttribute("error", "Please select a file");
                return "upload";
            }

            title = title.trim();
            subject = subject.trim();
            
            if (notesRepository.existsByTitle(title)) {
                model.addAttribute("error", "Title already exists!");
                return "upload";
            }

            // ===== Generate File Hash (Duplicate Protection) =====
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] fileBytes = file.getBytes();
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            String fileHash = sb.toString();

            if (notesRepository.existsByFileHash(fileHash)) {
                model.addAttribute("error", "This file already exists!");
                return "upload";
            }

            // ===== Extract Text From File =====
            String originalFilename = file.getOriginalFilename().toLowerCase();
            String extractedText = "";

            if (originalFilename.endsWith(".pdf")) {

                PDDocument document = PDDocument.load(file.getInputStream());
                PDFTextStripper stripper = new PDFTextStripper();
                extractedText = stripper.getText(document);
                document.close();

            } else if (originalFilename.endsWith(".pptx")) {

            	InputStream is = file.getInputStream();
            	XMLSlideShow ppt = new XMLSlideShow(is);

            	StringBuilder pptText = new StringBuilder();

            	for (XSLFSlide slide : ppt.getSlides()) {
            	    for (var shape : slide.getShapes()) {
            	        if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape) {
            	            pptText.append(textShape.getText()).append(" ");
            	        }
            	    }
            	}

            	extractedText = pptText.toString();
            	ppt.close();
            	
            } else if (originalFilename.endsWith(".docx")) {

            	XWPFDocument doc = new XWPFDocument(file.getInputStream());
            	XWPFWordExtractor extractor = new XWPFWordExtractor(doc);

            	extractedText = extractor.getText();

            	extractor.close();
            	doc.close();

            } else {
                model.addAttribute("error", "Only PDF, PPTX, DOCX allowed!");
                return "upload";
            }

            extractedText = extractedText.toLowerCase();

            // ===== Subject Keyword Validation =====
            boolean valid = false;
            List<String> keywords = subjectKeywords.get(subject);

            if (keywords != null && extractedText.length() > 0) {
                for (String keyword : keywords) {
                    if (extractedText.contains(keyword.toLowerCase())) {
                        valid = true;
                        break;
                    }
                }
            }

            if (!valid) {
                model.addAttribute("error",
                        "File content does not match selected subject!");
                return "upload";
            }

            // ===== Save File To Folder =====
            String filename = System.currentTimeMillis() + "_" + originalFilename;

            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            Path path = Paths.get(uploadDir + filename);
            Files.copy(file.getInputStream(), path,
                    StandardCopyOption.REPLACE_EXISTING);

            // ===== Save To Database =====
            Notes note = new Notes();
            note.setTitle(title);
            note.setYear(year);
            note.setBranch(branch);
            note.setSubject(subject);
            note.setFilename(filename);
            note.setUser(user);
            note.setFileSize(file.getSize());
            note.setUploadedAt(LocalDateTime.now());
            note.setFileHash(fileHash);

            notesRepository.save(note);

            createNotification(user.getUsername() +
                    " uploaded file for Year " + year + " " + branch);

            logActivity(user.getUsername(),
                    "Uploaded file: " + title);

            return "redirect:/dashboard?year=" + year + "&branch=" + branch;

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "File upload failed!");
            return "upload";
        }
    }

    // =========================
    // Download File (Protected)
    // =========================
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id,
                                                 HttpSession session) throws Exception {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        Notes note = notesRepository.findById(id).orElse(null);

        if (note == null) {
            return ResponseEntity.status(404).build();
        }

     // Increment note download count
        note.setDownloadCount(note.getDownloadCount() + 1);
        notesRepository.save(note);

        // Save download history
        DownloadHistory history = new DownloadHistory();
        history.setUser(user);
        history.setNote(note);
        history.setDownloadedAt(LocalDateTime.now());
        downloadHistoryRepository.save(history);
        

        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        Path path = Paths.get(uploadDir + note.getFilename());

        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + note.getFilename() + "\"")
                .body(resource);
    }

    // =========================
    // Delete File (Protected)
    // =========================
    @GetMapping("/delete/{id}")
    public String deleteNote(@PathVariable Long id,
                             HttpSession session) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        Notes note = notesRepository.findById(id).orElse(null);

        if (note == null ||
            note.getUser() == null ||
            !note.getUser().getId().equals(user.getId())) {

            return "redirect:/dashboard";
        }

        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        File file = new File(uploadDir + note.getFilename());

        if (file.exists()) {
            file.delete();
        }

        notesRepository.deleteById(id);
        logActivity(user.getUsername(), "Deleted file: " + note.getTitle());
        return "redirect:/dashboard";
    }
    
    @GetMapping("/edit/{id}")
    public String editPage(@PathVariable Long id,
                           HttpSession session,
                           Model model) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        Notes note = notesRepository.findById(id).orElse(null);

        if (note == null || !note.getUser().getId().equals(user.getId())) {
            return "redirect:/dashboard";
        }

        model.addAttribute("note", note);

        return "edit";
    }
    
    @PostMapping("/edit/{id}")
    public String updateNote(@PathVariable Long id,
                             @RequestParam String title,
                             HttpSession session) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        Notes note = notesRepository.findById(id).orElse(null);

        if (note == null || !note.getUser().getId().equals(user.getId())) {
            return "redirect:/dashboard";
        }

        note.setTitle(title);
        notesRepository.save(note);

        return "redirect:/dashboard";
    }
    
    @GetMapping("/admin")
    public String adminDashboard(Model model,
                                 HttpSession session,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "0") int notePage,
                                 @RequestParam(required = false) String noteKeyword,
                                 @RequestParam(required = false) String keyword) {

        User admin = (User) session.getAttribute("loggedUser");

        // ✅ Secure Check
        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return "redirect:/";
        }

        int pageSize = 5;

        // ================= USERS PAGINATION =================
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<User> userPage;
        if (keyword != null && !keyword.isEmpty()) {
            userPage = userRepository.findByUsernameContainingIgnoreCase(keyword, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        // ================= NOTES PAGINATION =================
        Pageable notesPageable = PageRequest.of(notePage, pageSize);
        Page<Notes> notesPage;
        if (noteKeyword != null && !noteKeyword.isEmpty()) {
            notesPage = notesRepository.findByTitleContainingIgnoreCase(noteKeyword, notesPageable);
        } else {
            notesPage = notesRepository.findAll(notesPageable);
        }

        // ================= STATS FOR CARDS & CHARTS =================
        Long totalStorage = notesRepository.getTotalStorageUsed();
        model.addAttribute("totalStorage", totalStorage != null ? totalStorage : 0L);
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalNotes", notesRepository.count());
        model.addAttribute("totalAdmins", userRepository.findByRole("ROLE_ADMIN").size());
        model.addAttribute("totalNormalUsers", userRepository.findByRole("ROLE_USER").size());

        // ✅ IMPORTANT: Leaderboard and Activity Logs
        model.addAttribute("recentLogs", activityLogRepository.findTop10ByOrderByTimestampDesc());
        
        // Pagination Attributes
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("keyword", keyword);

        model.addAttribute("allNotes", notesPage.getContent());
        model.addAttribute("notesCurrentPage", notePage);
        model.addAttribute("notesTotalPages", notesPage.getTotalPages());
        model.addAttribute("noteKeyword", noteKeyword);

        model.addAttribute("loggedUser", admin);

        return "admin";
    }
    
    @GetMapping("/admin/delete-user/{id}")
    public String deleteUser(@PathVariable Long id,
                             HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return "redirect:/";
        }

        User user = userRepository.findById(id).orElse(null);

        if (user != null && !user.getRole().equals("ROLE_ADMIN")) {

            List<Notes> notes = notesRepository.findByUser(user);
            notesRepository.deleteAll(notes);

            userRepository.delete(user);
        }

        return "redirect:/admin";
    }
   
    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        model.addAttribute("user", user);

        return "profile";
    }
    
    @GetMapping("/admin/notes-downloads")
    @ResponseBody
    public List<Object[]> getNotesDownloads(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return null;
        }

        return notesRepository.getNotesDownloadStats();
    }
    
    @GetMapping("/admin/change-role/{id}")
    public String changeUserRole(@PathVariable Long id,
                                 HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return "redirect:/";
        }

        User user = userRepository.findById(id).orElse(null);

        if (user != null && !user.getId().equals(admin.getId())) {

            if (user.getRole().equals("ROLE_USER")) {
                user.setRole("ROLE_ADMIN");
            } else {
                user.setRole("ROLE_USER");
            }

            userRepository.save(user);
            logActivity(admin.getUsername(),
                    "Changed role of " + user.getUsername());
            createNotification(user.getUsername() + " uploaded a file");
        }

        return "redirect:/admin";
    }
    
    
    @GetMapping("/admin/unlock/{id}")
    public String unlockUser(@PathVariable Long id, HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return "redirect:/";
        }

        User user = userRepository.findById(id).orElse(null);

        if (user != null) {
            user.setAccountLocked(false);
            user.setFailedAttempts(0);
            userRepository.save(user);

            logActivity(admin.getUsername(), 
                "Unlocked account of " + user.getUsername());
        }

        return "redirect:/admin";
    }
   
    @GetMapping("/admin/export-users")
    public ResponseEntity<String> exportUsers(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        List<User> users = userRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Username,Role,Registered Date,Last Login,Status\n");

        for (User u : users) {

            String lastLogin = (u.getLastLogin() != null)
                    ? u.getLastLogin().toString()
                    : "";

            String status = u.isAccountLocked() ? "Locked" : "Active";

            csv.append(u.getId()).append(",")
               .append(u.getUsername()).append(",")
               .append(u.getRole()).append(",")
               .append(u.getCreatedAt()).append(",")
               .append(lastLogin).append(",")
               .append(status)
               .append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=users_report.csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .body(csv.toString());
    }
    
    @GetMapping("/admin/export-logs")
    public ResponseEntity<String> exportLogs(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        List<ActivityLog> logs = activityLogRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Username,Action,Date & Time\n");

        for (ActivityLog log : logs) {
            csv.append(log.getId()).append(",")
               .append(log.getUsername()).append(",")
               .append(log.getAction()).append(",")
               .append(log.getTimestamp())
               .append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=activity_logs_report.csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .body(csv.toString());
    }
    
    @GetMapping("/admin/export-logs-filtered")
    public ResponseEntity<String> exportLogsFiltered(
            @RequestParam String fromDate,
            @RequestParam String toDate,
            HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        LocalDateTime start = LocalDateTime.parse(fromDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(toDate + "T23:59:59");

        List<ActivityLog> logs =
                activityLogRepository.findByTimestampBetween(start, end);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Username,Action,Date & Time\n");

        for (ActivityLog log : logs) {
            csv.append(log.getId()).append(",")
               .append(log.getUsername()).append(",")
               .append(log.getAction()).append(",")
               .append(log.getTimestamp())
               .append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=filtered_logs.csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .body(csv.toString());
    }
    
    @GetMapping("/admin/monthly-users")
    @ResponseBody
    public List<Object[]> getMonthlyUsers(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return null;
        }

        return userRepository.getMonthlyUserRegistrations();
    }
    
    @GetMapping("/admin/monthly-downloads")
    @ResponseBody
    public List<Object[]> getMonthlyDownloads(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return null;
        }

        return notesRepository.getMonthlyDownloads();
    }
    
    @GetMapping("/admin/notifications")
    @ResponseBody
    public List<Notification> getNotifications(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return Collections.emptyList();
        }

        return notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();
    }
    
    @PostMapping("/admin/notifications/read")
    @ResponseBody
    public void markNotificationsAsRead(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return;
        }

        List<Notification> unread =
                notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();

        for (Notification n : unread) {
            n.setRead(true);
        }

        notificationRepository.saveAll(unread);
    }
    
    @GetMapping("/admin/user/{id}")
    public String viewUserDetails(@PathVariable Long id,
                                  HttpSession session,
                                  Model model) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return "redirect:/";
        }

        User user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return "redirect:/admin";
        }

        // Get notes
        List<Notes> notes = notesRepository.findByUser(user);

        int totalDownloads = notes.stream()
                .mapToInt(Notes::getDownloadCount)
                .sum();

        // Get logs
        List<ActivityLog> userLogs =
                activityLogRepository.findByUsernameOrderByTimestampDesc(user.getUsername());

        // Calculate Risk Score
        int riskScore = 0;

        riskScore += user.getFailedAttempts() * 10;

        if (user.isAccountLocked()) {
            riskScore += 30;
        }

        if (totalDownloads > 50) {
            riskScore += 20;
        }

        if (userLogs.size() > 100) {
            riskScore += 20;
        }
     // 🔴 High Risk Check
        if (riskScore >= 70) {
            model.addAttribute("highRisk", true);
        } else {
            model.addAttribute("highRisk", false);
        }
        // Send to UI
        model.addAttribute("userDetail", user);
        model.addAttribute("userNotes", notes);
        model.addAttribute("totalNotes", notes.size());
        model.addAttribute("totalDownloads", totalDownloads);
        model.addAttribute("userLogs", userLogs);
        model.addAttribute("riskScore", riskScore);

        return "admin-user-detail";
    }
    
    @GetMapping("/admin/toggle-lock/{id}")
    public String toggleLock(@PathVariable Long id,
                             HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return "redirect:/";
        }

        User user = userRepository.findById(id).orElse(null);

        if (user != null && !user.getId().equals(admin.getId())) {

            user.setAccountLocked(!user.isAccountLocked());
            userRepository.save(user);

            createNotification("Account lock status changed for: " + user.getUsername());
        }

        return "redirect:/admin/user/" + id;
    }
    
    @GetMapping("/admin/user-monthly-uploads/{id}")
    @ResponseBody
    public List<Object[]> getUserMonthlyUploads(@PathVariable Long id,
                                                HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return null;
        }

        return notesRepository.getUserMonthlyUploads(id);
    }

    @GetMapping("/admin/user-growth")
    @ResponseBody
    public List<Object[]> getUserGrowth(HttpSession session) {
        User admin = (User) session.getAttribute("loggedUser");
        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return Collections.emptyList();
        }
        return userRepository.countUsersByDate();
    }
    
    @GetMapping("/admin/top-users")
    @ResponseBody
    public List<Object[]> getTopUsers(HttpSession session) {

        User admin = (User) session.getAttribute("loggedUser");

        if (admin == null || !admin.getRole().equals("ROLE_ADMIN")) {
            return Collections.emptyList();
        }

        Pageable topFive = PageRequest.of(0, 5);

        return notesRepository.getTopUsers(topFive).getContent();
    }
    
    @GetMapping("/verify")
    public String verifyUser(@RequestParam String token) {

        User user = userRepository.findByVerificationToken(token);

        if (user == null) {
            return "error";
        }

        if (user.isEnabled()) {
            return "verified";   // Already verified
        }

        user.setEnabled(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        return "verified";
    }
    
    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
                                        Model model) {

        User user = userRepository.findByEmail(email);

        if (user == null) {
            model.addAttribute("error", "Email not found!");
            return "forgot-password";
        }

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        userRepository.save(user);

        String resetLink = "http://localhost:8080/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("prajnasree78@gmail.com");
        message.setTo(user.getEmail());
        message.setSubject("Reset Your Password");
        message.setText("Click below link to reset your password:\n" + resetLink);

        mailSender.send(message);

        model.addAttribute("message", "Reset link sent to your email!");
        return "forgot-password";
    }
    
    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token,
                                    Model model) {

        User user = userRepository.findByResetToken(token);

        if (user == null) {
            return "error";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }
    
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                Model model) {

        User user = userRepository.findByResetToken(token);

        if (user == null) {
            return "error";
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setResetToken(null);
        userRepository.save(user);

        model.addAttribute("message", "Password updated successfully!");

        return "login";
    }
    
    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }
    
    @PostMapping("/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Old password incorrect!");
            return "redirect:/profile";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logActivity(user.getUsername(), "Changed password");
        sendPasswordChangeEmail(user);

        redirectAttributes.addFlashAttribute("success",
                "Password updated successfully!");

        return "redirect:/profile";
    }
    
    @GetMapping("/delete-account")
    public String deleteAccount(HttpSession session,
                                RedirectAttributes redirectAttributes) {

        User user = (User) session.getAttribute("loggedUser");

        if (user == null) {
            return "redirect:/";
        }

        sendAccountDeletedEmail(user);

        List<Notes> notes = notesRepository.findByUser(user);
        notesRepository.deleteAll(notes);

        userRepository.delete(user);

        session.invalidate();

        redirectAttributes.addFlashAttribute("success",
                "Your account has been deleted successfully!");

        return "redirect:/";
    }
    
}