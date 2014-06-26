package com.graphaware.crawler.integration;

import java.util.Arrays;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphaware.crawler.pagerank.PageRankModuleContext;
import com.graphaware.crawler.pagerank.RandomWalkerPageRankModule;
import com.graphaware.generator.GraphGenerator;
import com.graphaware.generator.Neo4jGraphGenerator;
import com.graphaware.generator.config.BasicGeneratorConfiguration;
import com.graphaware.generator.distribution.SimpleDegreeDistribution;
import com.graphaware.generator.node.SocialNetworkNodeCreator;
import com.graphaware.generator.relationship.SimpleGraphRelationshipGenerator;
import com.graphaware.generator.relationship.SocialNetworkRelationshipCreator;
import com.graphaware.runtime.state.GraphPosition;
import com.graphaware.runtime.state.ModuleContext;

/**
 * Integration tests for page rank module.  Note that it's not called "Test" in order to prevent Maven running it.
 */
@RunWith(JUnit4.class)
public class PageRankIntegration {

	private static final Logger LOG = LoggerFactory.getLogger(PageRankIntegration.class);

	private RandomWalkerPageRankModule pageRankModule;
	private GraphDatabaseService graphDatabaseService;
	private ExecutionEngine executionEngine;

	/** */
	@Before
	public void setUp() {
		this.graphDatabaseService = new TestGraphDatabaseFactory().newImpermanentDatabase();
		this.executionEngine = new ExecutionEngine(this.graphDatabaseService);

		this.pageRankModule = new RandomWalkerPageRankModule();
	}

	/** */
	@Test
	public void generateSocialNetworkAndWorkOutSomePageRankStatistics() {
		try (Transaction transaction = this.graphDatabaseService.beginTx()) {
			LOG.info("Creating arbitrary social network database...");

			long time = System.currentTimeMillis();
			GraphGenerator graphGenerator = new Neo4jGraphGenerator(this.graphDatabaseService);
			graphGenerator.generateGraph(new BasicGeneratorConfiguration(
					SocialNetworkNodeCreator.getInstance(),
					SocialNetworkRelationshipCreator.getInstance(),
					new SimpleGraphRelationshipGenerator(),
					new SimpleDegreeDistribution(Arrays.asList(1, 1, 1, 2, 3, 4, 4, 3, 3))));
			time = System.currentTimeMillis() - time;

			LOG.info("Created database in " + time + "ms");

			final int totalSteps = 100;
			LOG.info("Performing " + totalSteps + " steps to convergence on page rank");

			ModuleContext<GraphPosition<Node>> context = new PageRankModuleContext(null);
			time = System.currentTimeMillis();
			for (int i=0 ; i<totalSteps ; i++) {
				context = this.pageRankModule.doSomeWork(context, this.graphDatabaseService);
			}
			time = System.currentTimeMillis() - time;
			LOG.info("All steps completed in " + time + "ms");

			ExecutionResult result = this.executionEngine.execute("MATCH (node) RETURN node ORDER BY node.pageRankValue DESC");
			for (Map<String, Object> map : result) {
				Node n = (Node) map.get("node");
				LOG.info(n.getProperty("name") + " has " + n.getDegree() + " relationships and a page rank value of: "
						+ n.getProperty(RandomWalkerPageRankModule.PAGE_RANK_PROPERTY_KEY));
			}
		}
	}

}
