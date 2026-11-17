package com.toeic.backend.system.application;

import com.toeic.backend.system.application.AdminReadViews.*;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Probe nhẹ có deadline/pool hữu hạn; chỉ trả trạng thái và UTC, không lộ lỗi hoặc cấu hình. */
@Service
public class AdminHealthService {
    private final DataSource database;
    private final Clock clock;
    private final String aiUrl;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ExecutorService probes=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r -> {
        var thread=new Thread(r,"admin-health-probe");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    public AdminHealthService(DataSource database,Clock clock,@Value("${admin.monitoring.ai-health-url:}") String aiUrl,ObjectMapper json) {
        this.database=database;this.clock=clock;this.aiUrl=aiUrl;this.json=json;
    }
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('system:health:read')")
    public Health health() {
        var start=now();var components=new LinkedHashMap<String,Component>();
        components.put("backend",new Component("UP",start));
        components.put("database",new Component(probe(this::database),now()));
        components.put("ai",new Component(aiUrl==null || aiUrl.isBlank()?"NOT_CONFIGURED":probe(this::ai),now()));
        return new Health(start,components);
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC); }
    private String database() throws Exception {
        try(var connection=database.getConnection();var statement=connection.prepareStatement("SELECT 1")) {
            statement.setQueryTimeout(1);
            try(var result=statement.executeQuery()) {return result.next() && result.getInt(1)==1?"UP":"DOWN";}
        }
    }
    private String ai() throws Exception {
        URI uri;
        try {uri=URI.create(aiUrl);}catch(IllegalArgumentException exception){return "UNKNOWN";}
        if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)return "UNKNOWN";
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            if(response.statusCode()!=200)return "DOWN";
            var bytes=body.readNBytes(2049);if(bytes.length>2048)return "DOWN";
            var node=json.readTree(bytes);
            return node!=null && node.isObject() && "ok".equals(node.path("status").asString()) && "toeic-ai".equals(node.path("service").asString())?"UP":"DOWN";
        }
    }
    private String probe(Callable<String> action) {
        Future<String> future;
        try {future=probes.submit(action);}catch(RejectedExecutionException exception){return "UNKNOWN";}
        try {return future.get(2,TimeUnit.SECONDS);}
        catch(InterruptedException exception){Thread.currentThread().interrupt();return "UNKNOWN";}
        catch(ExecutionException|TimeoutException exception){return "DOWN";}
        finally {if(!future.isDone())future.cancel(true);}
    }
    @PreDestroy public void close() {probes.shutdownNow();}
}
