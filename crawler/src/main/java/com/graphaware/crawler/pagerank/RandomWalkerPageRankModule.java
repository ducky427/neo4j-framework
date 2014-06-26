package com.graphaware.crawler.pagerank;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import com.graphaware.runtime.config.RuntimeModuleConfiguration;
import com.graphaware.runtime.module.TimerDrivenRuntimeModule;
import com.graphaware.runtime.state.GraphPosition;
import com.graphaware.runtime.state.ModuleContext;

public class RandomWalkerPageRankModule implements TimerDrivenRuntimeModule<ModuleContext<GraphPosition<Node>>> {

	/** The name of the property key that is modified on visited nodes by this module. */
	public static final String PAGE_RANK_PROPERTY_KEY = "pageRankValue";

	private RandomNodeSelector randomNodeSelector;
	private RelationshipChooser relationshipChooser;

	public RandomWalkerPageRankModule() {
		this.randomNodeSelector = new HyperJumpRandomNodeSelector();
		// TODO: add relationship type and direction filtering based on module configuration
		this.relationshipChooser = new RandomRelationshipChooser();
	}

	@Override
	public void initialize(GraphDatabaseService database) {
		// nothing to do here
	}

	@Override
	public String getId() {
		throw new UnsupportedOperationException("atg hasn't written this method yet");
	}

	@Override
	public RuntimeModuleConfiguration getConfiguration() {
		throw new UnsupportedOperationException("atg hasn't written this method yet");
	}

	@Override
	public void reinitialize(GraphDatabaseService database) {
		throw new UnsupportedOperationException("atg hasn't written this method yet");
	}

	@Override
	public void shutdown() {
		throw new UnsupportedOperationException("atg hasn't written this method yet");
	}

	@Override
	public ModuleContext<GraphPosition<Node>> doSomeWork(ModuleContext<GraphPosition<Node>> lastContext, GraphDatabaseService graphDatabaseService) {
		// the first context is provided by the framework, which may not have any positional information on very first run
		GraphPosition<Node> graphPosition = lastContext.getPosition(); // XXX: will this be null for the first step?
		Node currentNode = graphPosition.find(graphDatabaseService);
		if (currentNode == null) {
			currentNode = this.randomNodeSelector.selectRandomNode(graphDatabaseService);
		}
		Node nextNode = determineNextNode(currentNode, graphDatabaseService);

		int pageRankValue = (int) nextNode.getProperty(PAGE_RANK_PROPERTY_KEY, 0);
		nextNode.setProperty(PAGE_RANK_PROPERTY_KEY, pageRankValue + 1);

		return new PageRankModuleContext(nextNode);
	}

	private Node determineNextNode(Node currentNode, GraphDatabaseService graphDatabaseService) {
		Relationship chosenRelationship = this.relationshipChooser.chooseRelationship(currentNode);
		if (chosenRelationship != null) {
			return chosenRelationship.getOtherNode(currentNode);
		}
		return this.randomNodeSelector.selectRandomNode(graphDatabaseService);
	}

}
