import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Importer
{
    private static final Map<String,String> userMap = new HashMap<>();

    /**
     * FIXME handle bug and new features (map trackers to labels)
     * FIXME avoid assigning an issue if it will be closed later (avoid emails)
     *
     *
     *
     * @param args
     * @throws RedmineException
     * @throws IOException
     */
    public static void main( String[] args ) throws RedmineException, IOException
    {
        String uri = "https://redmine.example.org";
        String apiAccessKey = "";
        String projectKey = "";
        Integer queryId = 118; // any

        // map redmine users to github users
        userMap.put( "g.toraldo", "gionn" );
        userMap.put( "manuel.mazzuola", "manuelmazzuola" );
        userMap.put( "matteo.giordano", "malteo" );

        RedmineManager mgr = RedmineManagerFactory.createWithApiKey(uri, apiAccessKey);

        GitHub github = GitHub.connect();

        // WARNING drop existing projects on github, reimport them from zero
        String[] repositories = new String[] { "ClouDesire/backend", "ClouDesire/frontend", "ClouDesire/ops" };

        for ( String repository : repositories )
        {
            try
            {
                final GHRepository githubRepository = github.getRepository( repository );
                githubRepository.delete();
            }
            catch ( FileNotFoundException ignored )
            {}

            github.getOrganization( "ClouDesire" ).createRepository( repository.replace( "ClouDesire/", "" ), repository, "", "Owners", false );
            System.out.println( "created " + repository );
        }
        // END WARNING

        final GHRepository backendRepository = github.getRepository( "ClouDesire/backend" );
        final GHRepository frontendRepository = github.getRepository( "ClouDesire/frontend" );
        final GHRepository opsRepository = github.getRepository( "ClouDesire/ops" );

        int counter = 0;
        List<Issue> redmineIssues = mgr.getIssueManager().getIssues(projectKey, queryId);
        for (Issue redmineIssue : redmineIssues) {

            GHRepository currentRepository = backendRepository;

            // HACK for switching from single redmine project to multiple github projects
            if (redmineIssue.getCategory() != null)
            {
                if (redmineIssue.getCategory().getName().matches( "(control panel)|(marketplace.*)|(cloudparty)|(cloudforge)" ))
                    currentRepository = frontendRepository;

                if (redmineIssue.getCategory().getName().matches( "(chef)|(operations)" ))
                    currentRepository = opsRepository;
            }
            // END HACK

            String ghAssignee = null;
            if (redmineIssue.getAssignee() != null)
            {
                final String login = redmineIssue.getAssignee().getFirstName();
                ghAssignee = userMap.get( login );
            }

            String categoryName = null;
            if (redmineIssue.getCategory() != null)
                categoryName = redmineIssue.getCategory().getName();

            final GHIssueBuilder ghIssueBuilder = currentRepository.createIssue( redmineIssue.getSubject() )
                    .body( redmineIssue.getDescription().concat( "\n\nREDMINE" + redmineIssue.getId() + "\n" ) );

            if (ghAssignee != null)
                ghIssueBuilder.assignee( ghAssignee );

            if (categoryName != null)
                ghIssueBuilder.label( categoryName );

            final GHIssue ghIssue = ghIssueBuilder.create();

            System.out.println("Created " + ghIssue.getId() + " into " + currentRepository.getName());

            if (redmineIssue.getStatusName().equals( "Closed" ) || redmineIssue.getStatusName().equals( "Refused" ) || redmineIssue.getStatusName().equals( "Fixed" ))
            {
                System.out.println("Closing " + ghIssue.getId());
                ghIssue.close();
            }

            //if (counter++ >= 10)
            //    break;
        }
    }

}
