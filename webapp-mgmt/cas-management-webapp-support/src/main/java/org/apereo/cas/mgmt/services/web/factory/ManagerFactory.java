package org.apereo.cas.mgmt.services.web.factory;

import org.apereo.cas.configuration.model.core.services.ServiceRegistryProperties;
import org.apereo.cas.mgmt.GitUtil;
import org.apereo.cas.mgmt.authentication.CasUserProfile;
import org.apereo.cas.mgmt.authentication.CasUserProfileFactory;
import org.apereo.cas.mgmt.configuration.CasManagementConfigurationProperties;
import org.apereo.cas.mgmt.services.GitServicesManager;
import org.apereo.cas.services.DefaultServicesManager;
import org.apereo.cas.services.DomainServicesManager;
import org.apereo.cas.services.JsonServiceRegistryDao;
import org.apereo.cas.services.ServicesManager;
import org.eclipse.jgit.api.Git;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Factory class to create ServiceManagers for the logged in user.
 *
 * @author Travis Schmidt
 * @since 5.2.0
 */
public class ManagerFactory {

    private final RepositoryFactory repositoryFactory;

    private final CasUserProfileFactory casUserProfileFactory;

    private final CasManagementConfigurationProperties casProperties;


    public ManagerFactory(final ServicesManager servicesManager,
                          final CasManagementConfigurationProperties casProperties,
                          final RepositoryFactory repositoryFactory,
                          final CasUserProfileFactory casUserProfileFactory) {
        this.repositoryFactory = repositoryFactory;
        this.casProperties = casProperties;
        this.casUserProfileFactory = casUserProfileFactory;
        final Path servicesRepo = Paths.get(casProperties.getMgmt().getServicesRepo());
        if (!Files.exists(servicesRepo)) {
            try {
                Git.init().setDirectory(servicesRepo.toFile()).call();
            } catch(final Exception e) {
                return;
            }
            try {
                final GitUtil git = repositoryFactory.masterRepository();
                final GitServicesManager manager = new GitServicesManager(createJSONServiceManager(git), git);
                manager.loadFrom(servicesManager);
                git.addWorkingChanges();
                git.getGit().commit().setAll(true).setMessage("Initial commit").call();
                git.setPublished();
                git.close();
            }catch (final Exception e) {
            }
        }
    }

    /**
     * Method will look up the CasUserProfile for the logged in user and the return the GitServicesManager for
     * that user.
     *
     * @param request - HttpServeltRequest
     * @param response - HttpServletRespone
     * @return - GitServicesManager for the logged in user
     * @throws Exception - failed
     */
    public GitServicesManager from(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        return from(request, casUserProfileFactory.from(request, response));
    }

    /**
     * Method will create the GitServicesManager for the user passed in the CasUserProfile.
     *
     * @param request - HttpServletRequest
     * @param user - CasUserProfile of logged in user
     * @return - GitServicesManager for the logged in user
     * @throws Exception - failed
     */
    public GitServicesManager from(final HttpServletRequest request, final CasUserProfile user) throws Exception {
        GitServicesManager manager = (GitServicesManager) request.getSession().getAttribute("servicesManager");
        if (manager != null) {
            manager.load();
        } else {
            final GitUtil git = repositoryFactory.from(user);
            manager = new GitServicesManager(createJSONServiceManager(git), git);
        }
        request.getSession().setAttribute("servicesManager", manager);
        return manager;
    }

    private ServicesManager createJSONServiceManager(final GitUtil git) {
        final ServicesManager manager;
        final JsonServiceRegistryDao serviceRegistryDAO = new JsonServiceRegistryDao(Paths.get(git.repoPath()),
                false,
                null, null);
        if (casProperties.getServiceRegistry().getManagementType() == ServiceRegistryProperties.ServiceManagementTypes.DOMAIN) {
            manager = new DomainServicesManager(serviceRegistryDAO, null);
        } else {
            manager = new DefaultServicesManager(serviceRegistryDAO, null);
        }
        manager.load();
        return manager;
    }
}
