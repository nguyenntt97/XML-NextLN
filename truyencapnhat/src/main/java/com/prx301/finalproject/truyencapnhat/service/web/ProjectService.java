package com.prx301.finalproject.truyencapnhat.service.web;

import com.prx301.finalproject.truyencapnhat.model.*;
import com.prx301.finalproject.truyencapnhat.model.crawler.model.ActivityLog;
import com.prx301.finalproject.truyencapnhat.model.crawler.model.ProjectActivity;
import com.prx301.finalproject.truyencapnhat.model.crawler.model.RecommendProject;
import com.prx301.finalproject.truyencapnhat.model.crawler.model.SimResult;
import com.prx301.finalproject.truyencapnhat.repository.AccountRepo;
import com.prx301.finalproject.truyencapnhat.repository.GenreRepo;
import com.prx301.finalproject.truyencapnhat.repository.ProjectRepo;
import com.prx301.finalproject.truyencapnhat.utils.ComUtils;
import com.prx301.finalproject.truyencapnhat.utils.JAXBUtils;
import com.prx301.finalproject.truyencapnhat.utils.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.StoredProcedureQuery;
import javax.xml.bind.JAXBException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.*;

@Service
public class ProjectService {
    private ProjectRepo projectRepo;
    private GenreRepo genreRepo;
    private AccountRepo accountRepo;

    private Map<String, ActivityLog> activityLogMap = new HashMap<>();

    @Autowired
    private EntityManager em;

    private final int DEFAULT_PAGE_SIZE = 20;

    public ProjectService(ProjectRepo projectRepo, GenreRepo genreRepo, AccountRepo accountRepo) {
        this.projectRepo = projectRepo;
        this.genreRepo = genreRepo;
        this.accountRepo = accountRepo;
        loadAllLogMap();
    }

    private void loadAllLogMap() {
        List<AccountEntity> allAcc = accountRepo.findAll();
        for (AccountEntity acc : allAcc) {
            if (acc.getActivity() != null && acc.getActivity().length() > 0) {
                ActivityLog activityLog = stringToActivity(acc.getActivity());
                if (activityLog != null) {
                    activityLogMap.put(acc.getUsername(), activityLog);
                }
            }
        }
    }

    public List<ProjectEntity> getAllProjects() {
        return projectRepo.findAll();
    }

    public ProjectEntity getProjectById(int id) {
        return projectRepo.findByProjectId(id);
    }


    public MostViewProjects getMostViewProjectEntities(int pageNo) {
        PageRequest pageRequest = PageRequest.of(pageNo, 5, Sort.by(Sort.Order.desc("projectView")));
        List<ProjectEntity> mostViewList = projectRepo.findAll(pageRequest).getContent();
        return new MostViewProjects(mostViewList);
    }

    public String getMostViewByXML() {
        List<ProjectEntity> projectEntities = projectRepo.findTop5ByOrderByProjectViewDesc();
        MostViewProjects mostViewProjects = new MostViewProjects(projectEntities);

        StringWriter stringWriter = new StringWriter();
        StreamResult streamResult = new StreamResult(stringWriter);

        try {
            JAXBUtils.objectToXML(mostViewProjects, streamResult);
        } catch (JAXBException e) {
            Logger.getLogger().log(Logger.LOG_LEVEL.ERROR, e, ProjectService.class);
        }
        return stringWriter.toString();
    }

    public SearchProjects searchProjectLikeName(String name) {
        List<ProjectEntity> projectEntities = projectRepo.findByProjectNameIgnoreCaseContaining(name);
        SearchProjects searchProjects = new SearchProjects(projectEntities);

        return searchProjects;
    }

    public SearchProjects searchProjectLikeName(String keyword, int pageNo) {
        StoredProcedureQuery searchQuery = em.createNamedStoredProcedureQuery("search_projects");

        searchQuery.setParameter("PageNumber", pageNo);
        searchQuery.setParameter("PageSize", DEFAULT_PAGE_SIZE);
        searchQuery.setParameter("Keyword", keyword);
        List<ProjectEntity> resultList = searchQuery.getResultList();
        int total = (int) searchQuery.getOutputParameterValue("Total");

        return new SearchProjects(total, resultList);
    }

    public SearchProjects getSameGenreList(int projectId) {
        List<ProjectEntity> recommendList = new ArrayList<>();
        ProjectEntity curProject = projectRepo.findByProjectId(projectId);
        if (curProject != null) {
            return new SearchProjects(0, new ArrayList<>());
        }
        int count = 0;
        for (GenreEntity genre : curProject.getGenres()) {
            if (count > 5) {
                break;
            }
            List<ProjectEntity> projectSameGenre = genre.getTemp();
            ProjectEntity chooseProject = ComUtils.pickRandom(projectSameGenre);
            recommendList.add(chooseProject);
            count++;
        }

        return new SearchProjects(count, recommendList);
    }

    private List<ProjectActivity> getUncomProjectList(ActivityLog act1, ActivityLog act2) {
        if (act1 == null || act2 == null) {
            return new ArrayList<>();
        }
        List<ProjectActivity> result = new ArrayList<>();
        for (ProjectActivity projectAct1 : act1.getLogList()) {
            boolean isExist = false;
            for (ProjectActivity projectAct2 : act2.getLogList()) {
                if (projectAct1.getProjectId() == projectAct2.getProjectId()) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                result.add(projectAct1);
            }
        }
        return result;
    }

    private RecommendProject checkRecomExist(int projectId, List<RecommendProject> curList) {
        for (RecommendProject recommendProject : curList) {
            if (recommendProject.getProjectId() == projectId) {
                return recommendProject;
            }
        }
        return null;
    }

    private ProjectActivity checkProjectActExist(int projectId, List<ProjectActivity> actList) {
        for (ProjectActivity activity : actList) {
            if (activity.getProjectId() == projectId) {
                return activity;
            }
        }
        return null;
    }

    public List<ProjectEntity> getProjectRecomList(String username) {
        List<RecommendProject> recommendProjectList = getPeopleRecommendList(username);
        List<ProjectEntity> recomProjects = new ArrayList<>();

        for (RecommendProject recommendProject : recommendProjectList) {
            ProjectEntity projectEntity = projectRepo.findByProjectId(recommendProject.getProjectId());

            recomProjects.add(projectEntity);
        }

        return recomProjects;
    }

    public List<RecommendProject> getPeopleRecommendList(String username) {
        ActivityLog myActLog = activityLogMap.get(username);
        List<RecommendProject> recommendProjectList = new ArrayList<>();

        if (myActLog == null) {
            return new ArrayList<>();
        }

        List<SimResult> simResults = getTop5SimilarUser(myActLog, username);

        for (SimResult userRs : simResults) {
            ActivityLog activityLog = activityLogMap.get(userRs.getUsername());
            List<ProjectActivity> unCommonProjects = getUncomProjectList(activityLog, myActLog);

            for (ProjectActivity uncomProject : unCommonProjects) {
                double w = uncomProject.getRating() * userRs.getSimilarity();
                RecommendProject existRec = checkRecomExist(uncomProject.getProjectId(), recommendProjectList);
                if (existRec == null) {
                    existRec = new RecommendProject(uncomProject.getProjectId());
                    recommendProjectList.add(existRec);
                }
                existRec.addNewProject(w, userRs.getSimilarity());
            }
        }

        for (RecommendProject recommendProject : recommendProjectList) {
            recommendProject.calcFinalScore();
        }

        recommendProjectList.sort(new Comparator<RecommendProject>() {
            @Override
            public int compare(RecommendProject o1, RecommendProject o2) {
                return (int) (o1.getFinalScore() - o2.getFinalScore());
            }
        });

        int bound = 5;
        if (recommendProjectList.size() < 5) {
            bound = recommendProjectList.size();
        }

        return recommendProjectList.subList(0, bound);
    }

    private List getTop5SimilarUser(ActivityLog myActLog, String username) {
        List<SimResult> results = new ArrayList<>();

        for (Map.Entry<String, ActivityLog> user : activityLogMap.entrySet()) {
            if (!user.getKey().equals(username)) {
                double sim = ComUtils.calcEuclidDist(myActLog, user.getValue());
                SimResult simResult = new SimResult(user.getKey(), sim);
                results.add(simResult);
            }
        }

        results.sort((o1, o2) -> (int) (o1.getSimilarity() - o2.getSimilarity()));
        // Top 5 similar users
        int bound = 5;
        if (results.size() < 5) {
            bound = results.size();
        }

        return results.subList(0, bound);
    }

    public void rateProject(AccountEntity accountEntity, int projectId, int point) {
        if (accountEntity == null) {
            return;
        }
        ActivityLog existLog = activityLogMap.get(accountEntity.getUsername());
        ProjectEntity projectEntity = projectRepo.findByProjectId(projectId);
        if (projectEntity == null) {
            return;
        }

        if (existLog == null) {
            List<ProjectActivity> projectActivities = new ArrayList<>();
            projectActivities.add(new ProjectActivity(projectId, point));

            existLog = new ActivityLog(projectActivities);
        } else {
            List<ProjectActivity> projectActivities = existLog.getLogList();
            ProjectActivity existAct = checkProjectActExist(projectId, projectActivities);
            if (existAct == null) {
                existAct = new ProjectActivity(projectId, point);
                projectActivities.add(existAct);
            } else {
                existAct.setRating(point);
            }
        }

        String updateLogString = activityLogToString(existLog);
        accountEntity.setActivity(updateLogString);

        accountRepo.save(accountEntity);

        activityLogMap.put(accountEntity.getUsername(), existLog);
    }

    private String activityLogToString(ActivityLog log) {
        StringWriter writer = new StringWriter();
        StreamResult streamResult = new StreamResult(writer);

        try {
            JAXBUtils.objectToXML(log, streamResult);
        } catch (JAXBException e) {
            Logger.getLogger().log(Logger.LOG_LEVEL.ERROR, e, ProjectService.class);
        }

        return writer.toString();
    }

    private ActivityLog stringToActivity(String log) {
        DOMResult result = ComUtils.StringToDOMResult(log, ActivityLog.class);
        try {
            ActivityLog activityLog = JAXBUtils.xmlToObject(result, ActivityLog.class);
            if (activityLog != null) {
                return activityLog;
            }
        } catch (JAXBException e) {
            Logger.getLogger().log(Logger.LOG_LEVEL.ERROR, e, ProjectService.class);
        }

        return null;
    }

}
