import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

public class App {
    public static void main(String[] args) {
        Monitoring monitoring = new Monitoring();
        // 환경변수에서 키워드를 가져와 뉴스를 분석하고 LLM 기반 전망 생성
        String keyword = System.getenv("KEYWORD");
        monitoring.getNewsAndAnalysis(keyword, 10, 1, SortType.date);
    }
}

enum SortType {
    sim("sim"), date("date");

    final String value;

    SortType(String value) {
        this.value = value;
    }
}

class Monitoring {
    private final Logger logger;
    private final HttpClient client;
    private final String REPO_PATH;
    private final SimpleDateFormat dateFormat;

    public Monitoring() {
        logger = Logger.getLogger(Monitoring.class.getName());
        client = HttpClient.newHttpClient();
        // GitHub 저장소의 로컬 경로 설정 (환경 변수로 설정하거나 하드코딩)
        REPO_PATH = System.getenv("REPO_PATH") != null ? 
            System.getenv("REPO_PATH") : "/path/to/your/github/repo";
        dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        logger.info("Monitoring 객체 생성");
    }

    // 1. 검색어를 통해서 뉴스를 받아오고 LLM 분석까지 수행
    public void getNewsAndAnalysis(String keyword, int display, int start, SortType sort) {
        String imageLink = "";
        try {
            // 뉴스 데이터 가져오기
            String response = getDataFromAPI("news.json", keyword, display, start, sort);

            // 뉴스 제목 추출
            String[] tmp = response.split("title\":\"");
            String[] titles = new String[display];
            String[] originalLinks = new String[display];

            // 원문 링크 추출
            String[] tmpLinks = response.split("originallink\":\"");
            for (int i = 1; i < tmpLinks.length && i <= display; i++) {
                originalLinks[i - 1] = tmpLinks[i].split("\",")[0]
                                        .replaceAll("\\\\", ""); // 이스케이프된 백슬래시 제거
            }

            // 제목 추출
            for (int i = 1; i < tmp.length; i++) {
                titles[i - 1] = tmp[i].split("\",")[0]
                                .replaceAll("</?b>", "") // <b> 및 </b> 태그 제거
                                .replaceAll("\\\\", ""); // 이스케이프된 백슬래시 제거

            }
            logger.info(Arrays.toString(titles));

            // 뉴스 내용(description) 추출
            String[] descriptions = new String[display];
            String[] tmpDesc = response.split("description\":\"");
            for (int i = 1; i < tmpDesc.length; i++) {
                descriptions[i - 1] = tmpDesc[i].split("\",")[0]
                        .replaceAll("<b>", "")
                        .replaceAll("</b>", "");
            }

            // 뉴스 정보를 합쳐서 LLM 분석을 위한 컨텍스트 생성
            StringBuilder newsContext = new StringBuilder();
            for (int i = 0; i < titles.length && i < descriptions.length; i++) {
                if (titles[i] != null && !titles[i].isEmpty()) {
                    newsContext.append("뉴스 ").append(i + 1).append(": ").append(titles[i]).append("\n");
                    if (descriptions[i] != null && !descriptions[i].isEmpty()) {
                        newsContext.append("내용: ").append(descriptions[i]).append("\n\n");
                    }
                }
            }

            // LLM을 통한 뉴스 분석 및 전망 생성
            String analysisResult = analyzeTrendsWithLLM(keyword, newsContext.toString());
            
            // 현재 날짜 가져오기
            String currentDate = dateFormat.format(new Date());

            // 현재 날짜를 기준으로 start 파라미터 변경
            Calendar cal = Calendar.getInstance();
            int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);

            // 날짜에 따라 1~50 사이의 값으로 start 파라미터 순환
            int start_image = (dayOfYear % 50) + 1;
            
            // 이미지 다운로드
            String imageResponse = getDataFromAPI("image", keyword, display, start_image, SortType.sim);
            imageLink = imageResponse
                    .split("link\":\"")[1].split("\",")[0]
                    .split("\\?")[0]
                    .replace("\\", "");
            logger.info(imageLink);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageLink))
                    .build();
            
            String[] tmp2 = imageLink.split("\\.");
            String imageExtension = tmp2[tmp2.length - 1];
            String imageName = keyword + "_" + currentDate + "." + imageExtension;
            Path imagePath = Paths.get(REPO_PATH, "images", imageName);
            
            // 이미지 디렉토리 생성
            Files.createDirectories(Paths.get(REPO_PATH, "images"));
            
            // 이미지 저장
            client.send(request, HttpResponse.BodyHandlers.ofFile(imagePath));
            
            // Markdown 파일 생성 (날짜별 분석)
            String markdownContent = createMarkdownContent(keyword, titles, originalLinks, analysisResult, imageName, currentDate);
            Path markdownPath = Paths.get(REPO_PATH, "_posts", currentDate + "-" + keyword + "-analysis.md");
            
            // _posts 디렉토리 생성
            Files.createDirectories(Paths.get(REPO_PATH, "_posts"));
            
            // Markdown 저장
            Files.writeString(markdownPath, markdownContent);
            
            // 인덱스 페이지 업데이트
            updateIndexPage(keyword);
            
        
            
            logger.info("뉴스 분석 및 이미지 다운로드 완료, GitHub Pages 업데이트됨");

        } catch (Exception e) {
            logger.severe("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Markdown 형식의 콘텐츠 생성
    private String createMarkdownContent(String keyword, String[] titles, String[] originalLinks, String analysisResult, String imageName, String currentDate) {
        StringBuilder markdown = new StringBuilder();
        
        // Front matter 추가 (Jekyll 용)
        markdown.append("---\n");
        markdown.append("layout: post\n");
        markdown.append("title: \"").append(keyword).append(" 트렌드 분석 - ").append(currentDate).append("\"\n");
        markdown.append("date: ").append(currentDate).append("\n");
        markdown.append("categories: analysis\n");
        markdown.append("tags: [").append(keyword).append(", trends, analysis]\n");
        markdown.append("image: /images/").append(imageName).append("\n");
        markdown.append("---\n\n");
        
        // 헤더 추가
        markdown.append("# ").append(keyword).append("에 대한 트렌드 분석 (").append(currentDate).append(")\n\n");
        
        // 이미지 추가
        markdown.append("<img src=\"https://nan0silver.github.io/auto_monitoring/images/").append(imageName).append("\" alt=\"").append(keyword).append(" 관련 이미지\" width=\"300\">\n\n");
         
        // 뉴스 목록 추가 - 하이퍼링크로 변경
        markdown.append("## 오늘의 주요 뉴스\n\n");
        for (int i = 0; i < titles.length; i++) {
            if (titles[i] != null && !titles[i].isEmpty()) {
                // 하이퍼링크 형식으로 제목 추가 (URL 인코딩 검사 추가)
                if (originalLinks[i] != null && !originalLinks[i].isEmpty()) {
                    String safeUrl = originalLinks[i];
                    if (safeUrl.contains("(") || safeUrl.contains(")")) {
                        // URL에 괄호가 있는 경우 <> 로 감싸서 처리
                        markdown.append("- [").append(titles[i]).append("](<").append(safeUrl).append(">)\n");
                    } else {
                        markdown.append("- [").append(titles[i]).append("](").append(safeUrl).append(")\n");
                    }
                } else {
                    // 링크가 없는 경우 일반 텍스트로 추가
                    markdown.append("- ").append(titles[i]).append("\n");
                }
            }
        }
        markdown.append("\n");
        
        // LLM 분석 결과 추가
        markdown.append("## 트렌드 분석\n\n");
        markdown.append(analysisResult.replace("\n", "\n\n"));
        
        return markdown.toString();
    }
    
    // 인덱스 페이지 업데이트
    private void updateIndexPage(String keyword) throws Exception {
        Path indexPath = Paths.get(REPO_PATH, "index.md");
        
        // 기본 인덱스 페이지가 없으면 생성
        if (!Files.exists(indexPath)) {
            StringBuilder indexContent = new StringBuilder();
            indexContent.append("---\n");
            indexContent.append("layout: home\n");
            indexContent.append("title: \"").append(keyword).append(" 트렌드 대시보드\"\n");
            indexContent.append("---\n\n");
            //indexContent.append("# ").append(keyword).append(" 트렌드 대시보드\n\n");
            indexContent.append("이 사이트는 '").append(keyword).append("'에 관한 뉴스 트렌드와 LLM 기반 분석을 매일 제공합니다.\n\n");
            indexContent.append("## 최근 분석\n\n");
            indexContent.append("아래 목록에서 날짜별 분석 결과를 확인할 수 있습니다.\n\n");
            
            Files.writeString(indexPath, indexContent.toString());
        }
    }
    
    

    // LLM을 사용하여 뉴스 트렌드 분석 및 미래 전망 생성 (순수 Java로 JSON 처리)
    private String analyzeTrendsWithLLM(String keyword, String newsContext) throws Exception {
        try {
            String openaiApiKey = System.getenv("OPENAI_API_KEY");
            if (openaiApiKey == null || openaiApiKey.isEmpty()) {
                throw new Exception("OPENAI_API_KEY 환경 변수가 설정되지 않았습니다.");
            }

            // OpenAI API에 보낼 프롬프트 구성
            String prompt = String.format(
                    """
                    당신은 '%s' 키워드에 대한 전문가입니다. 다음 뉴스 정보를 분석하고 아래 형식으로 응답해주세요:
                    
                    [뉴스 정보]
                    %s
                    
                    응답 형식:
                    1. 현재 동향 요약: 위 뉴스들에서 파악된 '%s'에 관한 주요 트렌드와 현재 상황 분석
                    2. 핵심 포인트: 중요한 이슈나 변화 3-5가지 요약 (불릿 포인트)
                    3. 전문가 관점의 미래 전망: '%s'의 향후 발전 방향과 예상되는 변화
                    4. 관련 산업 영향: 이 트렌드가 연관 산업이나 분야에 미칠 수 있는 영향
                    """,
                    keyword, newsContext, keyword, keyword
            );

            // OpenAI API에 보낼 JSON 직접 구성
            String requestBody = String.format(
                    """
                    {
                      "model": "gpt-4",
                      "messages": [
                        {
                          "role": "user",
                          "content": "%s"
                        }
                      ],
                      "temperature": 0.7,
                      "max_tokens": 1500
                    }
                    """,
                    // JSON 내에서 사용할 수 있도록 특수 문자 이스케이프
                    prompt.replace("\"", "\\\"").replace("\n", "\\n")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.severe("LLM API 오류: " + response.statusCode() + " - " + response.body());
                return "LLM 분석 실패: API 오류 발생";
            }

            // OpenAI API 응답 파싱 (수정된 부분)
            return extractContentFromResponse(response.body());

        } catch (Exception e) {
            logger.severe("LLM 분석 중 오류: " + e.getMessage());
            throw new Exception("LLM 분석 실패: " + e.getMessage());
        }
    }

    private String extractContentFromResponse(String responseBody) {
        try {
            // "choices" 배열의 첫 번째 항목의 "message" 객체의 "content" 값을 추출
            String choicesKey = "\"choices\":";
            int choicesIndex = responseBody.indexOf(choicesKey);

            if (choicesIndex != -1) {
                int messageIndex = responseBody.indexOf("\"message\":", choicesIndex);

                if (messageIndex != -1) {
                    int contentIndex = responseBody.indexOf("\"content\":", messageIndex);

                    if (contentIndex != -1) {
                        // "content":" 다음의 따옴표 위치 찾기
                        int valueStartIndex = responseBody.indexOf("\"", contentIndex + 10) + 1;

                        // 내용의 끝을 찾기 (다음 따옴표의 위치, 단 이스케이프된 따옴표는 제외)
                        int valueEndIndex = valueStartIndex;
                        boolean escaped = false;

                        while (true) {
                            valueEndIndex = responseBody.indexOf("\"", valueEndIndex);

                            if (valueEndIndex == -1) {
                                break;
                            }

                            // 이전 문자가 이스케이프 문자(\)인지 확인
                            if (responseBody.charAt(valueEndIndex - 1) == '\\') {
                                // 이스케이프된 따옴표라면 다음 위치부터 다시 찾기
                                valueEndIndex++;
                                continue;
                            }

                            // 이스케이프되지 않은 따옴표를 찾았으므로 종료
                            break;
                        }

                        if (valueEndIndex != -1) {
                            String extractedContent = responseBody.substring(valueStartIndex, valueEndIndex)
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\");
                            return extractedContent;
                        }
                    }
                }
            }

            return "LLM 응답 처리 실패: 응답 구조에서 'content' 필드를 찾을 수 없습니다.";
        } catch (Exception e) {
            return "LLM 응답 처리 실패: " + e.getMessage();
        }
    }

    private String getDataFromAPI(String path, String keyword, int display, int start, SortType sort) throws Exception {
        String url = "https://openapi.naver.com/v1/search/%s".formatted(path);
        String params = "query=%s&display=%d&start=%d&sort=%s".formatted(
                keyword, display, start, sort.value
        );

        String clientId = System.getenv("NAVER_CLIENT_ID");
        String clientSecret = System.getenv("NAVER_CLIENT_SECRET");

        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            throw new Exception("NAVER API 키가 설정되지 않았습니다.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + params))
                .GET()
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            logger.info("API 응답 코드: " + response.statusCode());

            if (response.statusCode() != 200) {
                logger.severe("API 오류: " + response.body());
                throw new Exception("API 응답 오류: " + response.statusCode());
            }

            return response.body();

        } catch (Exception e) {
            logger.severe("API 연결 오류: " + e.getMessage());
            throw new Exception("네이버 API 연결 오류: " + e.getMessage());
        }
    }
}