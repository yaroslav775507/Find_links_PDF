package com.example.interface_for_pd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.interface_for_pd.st.StorageFileNotFoundException;
import com.example.interface_for_pd.st.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileUploadController {
    private final StorageService storageService;
    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }
    @GetMapping("/")
    public String listUploadedFiles(Model model) {
        model.addAttribute("files", storageService.loadAll().map(
                        path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class,
                                "serveFile", path.getFileName().toString()).build().toUri().toString())
                .collect(Collectors.toList()));
        return "uploadForm";
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<String> serveFile(@PathVariable String filename) {
        Resource file = storageService.loadAsResource(filename);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String content = textStripper.getText(document);
            List<String> links = new ArrayList<>();
            Matcher matcher = Pattern.compile("https?://\\S+").matcher(content);
            while (matcher.find()) {
                links.add(matcher.group());
            }
            if (links.isEmpty()) {
                return ResponseEntity.ok().body("No links found in the PDF file");
            } else {
                String linksHtml = generateLinksHtml(links); // Генерация HTML для ссылок
                return ResponseEntity.ok().body(linksHtml);
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading PDF file");
        }
    }

    private String generateLinksHtml(List<String> links) {
        StringBuilder html = new StringBuilder();
        html.append("<html>");
        html.append("<head><title>Links</title></head>");
        html.append("<body style=\"background-color: #F67861;\">"); // Голубой фон
        html.append("<h1>Ссылки найденные в документе:</h1>");
        html.append("<ul>");
        for (String link : links) {
            html.append("<li><a href=\"").append(link).append("\">").append(link).append("</a></li>");
        }
        html.append("</ul>");
        html.append("</body>");
        html.append("</html>");
        return html.toString();
    }

    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        storageService.store(file);
        redirectAttributes.addFlashAttribute("message",
                "Вы успешно загрузили файл " + file.getOriginalFilename() + "!");
        return "redirect:/";
    }
    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

}
