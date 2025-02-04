package org.fog.scheduling.gaEntities;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.fog.entities.FogDevice;
import org.fog.scheduling.SchedulingAlgorithm;

/**
 * The GeneticAlgorithm class is our main abstraction for managing the
 * operations of the genetic algorithm. This class is meant to be
 * problem-specific, meaning that (for instance) the "calcFitness" method may
 * need to change from problem to problem.
 *
 * This class concerns itself mostly with population-level operations, but also
 * problem-specific operations such as calculating fitness, testing for
 * termination criteria, and managing mutation and crossover operations (which
 * generally need to be problem-specific as well).
 *
 * Generally, GeneticAlgorithm might be better suited as an abstract class or an
 * interface, rather than a concrete class as below. A GeneticAlgorithm
 * interface would require implementation of methods such as
 * "isTerminationConditionMet", "calcFitness", "mutatePopulation", etc, and a
 * concrete class would be defined to solve a particular problem domain. For
 * instance, the concrete class "TravelingSalesmanGeneticAlgorithm" would
 * implement the "GeneticAlgorithm" interface. This is not the approach we've
 * chosen, however, so that we can keep each chapter's examples as simple and
 * concrete as possible.
 *
 * @author bkanber
 *
 */
public class GeneticAlgorithm {
	private int populationSize;

	/**
	 * Mutation rate is the fractional probability than an individual gene will
	 * mutate randomly in a given generation. The range is 0.0-1.0, but is generally
	 * small (on the order of 0.1 or less).
	 */
	private double mutationRate;

	/**
	 * Crossover rate is the fractional probability that two individuals will "mate"
	 * with each other, sharing genetic information, and creating offspring with
	 * traits of each of the parents. Like mutation rate the rance is 0.0-1.0 but
	 * small.
	 */
	private double crossoverRate;

	/**
	 * Elitism is the concept that the strongest members of the population should be
	 * preserved from generation to generation. If an individual is one of the
	 * elite, it will not be mutated or crossover.
	 */
	private int elitismCount;

	private double minTime;
	private double minCost;

	public GeneticAlgorithm(int populationSize, double mutationRate, double crossoverRate, int elitismCount) {
		this.populationSize = populationSize;
		this.mutationRate = mutationRate;
		this.crossoverRate = crossoverRate;
		this.elitismCount = elitismCount;
	}

	/**
	 * calculate the lower boundary of time and cost
	 *
	 */
	public void calcMinTimeCost(List<FogDevice> fogDevices, List<? extends Cloudlet> cloudletList) {
		this.minTime = calcMinTime(fogDevices, cloudletList);
		this.minCost = calcMinCost(fogDevices, cloudletList);
	}

	private double calcMinCost(List<FogDevice> fogDevices, List<? extends Cloudlet> cloudletList) {
		double minCost = 0;
		for (Cloudlet cloudlet : cloudletList) {
			double minCloudletCost = Double.MAX_VALUE;
			for (FogDevice fogDevice : fogDevices) {
				double cost = calcCost(cloudlet, fogDevice);
				if (minCloudletCost > cost) {
					minCloudletCost = cost;
				}
			}
			// the minCost is defined as the sum of all minCloudletCost
			minCost += minCloudletCost;
		}
		return minCost;
	}

	// the method calculates the cost (G$) when a fogDevice executes a cloudlet
	private double calcCost(Cloudlet cloudlet, FogDevice fogDevice) {
		double cost = 0;
		// cost includes the processing cost
		cost += fogDevice.getCharacteristics().getCostPerSecond() * cloudlet.getCloudletLength()
				/ fogDevice.getHost().getTotalMips();
		// cost includes the memory cost
		cost += fogDevice.getCharacteristics().getCostPerMem() * cloudlet.getMemRequired();
		// cost includes the bandwidth cost
		cost += fogDevice.getCharacteristics().getCostPerBw()
				* (cloudlet.getCloudletFileSize() + cloudlet.getCloudletOutputSize());
		return cost;
	}

	// the function calculate the lower bound of the solution about time execution
	private double calcMinTime(List<FogDevice> fogDevices, List<? extends Cloudlet> cloudletList) {
		double minTime = 0;
		double totalLength = 0;
		double totalMips = 0;
		for (Cloudlet cloudlet : cloudletList) {
			totalLength += cloudlet.getCloudletLength();
		}
		for (FogDevice fogDevice : fogDevices) {
			totalMips += fogDevice.getHost().getTotalMips();
		}
		minTime = totalLength / totalMips;
		return minTime;
	}

	/**
	 * Initialize population
	 *
	 * @param chromosomeLength The length of the individuals chromosome
	 * @return population The initial population generated
	 */
	public Population initPopulation(int chromosomeLength, int maxValue) {
		// Initialize population
		Population population = new Population(this.populationSize, chromosomeLength, maxValue);
		return population;
	}

	/**
	 * Calculate fitness for an individual.
	 *
	 * In this case, the fitness score is very simple: it's the number of ones in
	 * the chromosome. Don't forget that this method, and this whole
	 * GeneticAlgorithm class, is meant to solve the problem in the "AllOnesGA"
	 * class and example. For different problems, you'll need to create a different
	 * version of this method to appropriately calculate the fitness of an
	 * individual.
	 *
	 * @param individual the individual to evaluate
	 * @return double The fitness value for individual
	 */
	public double calcFitness(Individual individual, List<FogDevice> fogDevices,
			List<? extends Cloudlet> cloudletList) {

		// clear the fogDevice - task list before calculate
		for (FogDevice fogDevice : fogDevices) {
			fogDevice.getCloudletListAssignment().clear();
		}

		// Loop over individual's genes to all the task assigned to the fogDevice
		for (int geneIndex = 0; geneIndex < individual.getChromosomeLength(); geneIndex++) {
			// add current cloudlet to fog device respectively
			fogDevices.get(individual.getGene(geneIndex)).getCloudletListAssignment().add(cloudletList.get(geneIndex));
		}

		// Calculate makespan and cost
		double makespan = 0;
		double execTime = 0;
		double totalCost = 0;
		for (FogDevice fogDevice : fogDevices) {
			double totalLength = 0;
			for (Cloudlet cloudlet : fogDevice.getCloudletListAssignment()) {
				totalLength += cloudlet.getCloudletLength();
				// the total cost is sum of the cost execution of each cloudlet
				totalCost += calcCost(cloudlet, fogDevice);
			}
			// execTime is the time that fogDevice finishes its list cloudlet assignment
			execTime = totalLength / fogDevice.getHostList().get(0).getTotalMips();
			// makespan is defined as when the last cloudlet finished or when all fogDevices
			// finish its work.
			if (execTime > makespan) {
				makespan = execTime;
			}
		}

		// store makespan
		individual.setTime(makespan);
		// store cost
		individual.setCost(totalCost);

		// Calculate fitness
		double fitness = SchedulingAlgorithm.TIME_WEIGHT * minTime / makespan
				+ (1 - SchedulingAlgorithm.TIME_WEIGHT) * minCost / totalCost;

		// Store fitness
		individual.setFitness(fitness);
		return fitness;
	}

	/**
	 * Evaluate the whole population
	 *
	 * Essentially, loop over the individuals in the population, calculate the
	 * fitness for each, and then calculate the entire population's fitness. The
	 * population's fitness may or may not be important, but what is important here
	 * is making sure that each individual gets evaluated.
	 *
	 * @param population the population to evaluate
	 */
	public Population evalPopulation(Population population, List<FogDevice> fogDevices,
			List<? extends Cloudlet> cloudletList) {

		double populationFitness = 0;

		// Loop over population evaluating individuals and summing population fitness
		for (Individual individual : population.getPopulation()) {
			populationFitness += calcFitness(individual, fogDevices, cloudletList);
		}

		// sort population with increasing fitness value
		population.sortPopulation();

		population.setPopulationFitness(populationFitness);
		return population;
	}

	/**
	 * Check if population has met termination condition
	 *
	 * For this simple problem, we know what a perfect solution looks like, so we
	 * can simply stop evolving once we've reached a fitness of one.
	 *
	 * @param population
	 * @return boolean True if termination condition met, otherwise, false
	 */
	public boolean isTerminationConditionMet(Population population) {
		for (Individual individual : population.getPopulation()) {
			if (individual.getFitness() == 1) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Select parent for crossover
	 *
	 * @param population The population to select parent from
	 * @return The individual selected as a parent
	 */
	public Individual selectIndividual(Population population) {
		// Get individuals
		List<Individual> individuals = population.getPopulation();

		// Spin roulette wheel
		double populationFitness = population.getPopulationFitness();
		double rouletteWheelPosition = Math.random() * populationFitness;

		// Find parent
		double spinWheel = 0;
		for (Individual individual : individuals) {
			spinWheel += individual.getFitness();
			if (spinWheel >= rouletteWheelPosition) {
				return individual;
			}
		}
		return individuals.get(population.size() - 1);
	}

	/**
	 * Apply crossover to population
	 *
	 * Crossover, more colloquially considered "mating", takes the population and
	 * blends individuals to create new offspring. It is hoped that when two
	 * individuals crossover that their offspring will have the strongest qualities
	 * of each of the parents. Of course, it's possible that an offspring will end
	 * up with the weakest qualities of each parent.
	 *
	 * This method considers both the GeneticAlgorithm instance's crossoverRate and
	 * the elitismCount.
	 *
	 * The type of crossover we perform depends on the problem domain. We don't want
	 * to create invalid solutions with crossover, so this method will need to be
	 * changed for different types of problems.
	 *
	 * This particular crossover method selects random genes from each parent.
	 *
	 * @param population The population to apply crossover to
	 * @return The new population
	 */
	public Population crossoverPopulation(Population population, List<FogDevice> fogDevices,
			List<? extends Cloudlet> cloudletList) {
		// Create new population
		List<Individual> newPopulation = new ArrayList<Individual>();

		newPopulation.clear();
		// Loop over current population by fitness
		for (int populationIndex = 0; populationIndex < population.size(); populationIndex++) {
			Individual parent1 = population.getFittest(populationIndex);

			// Apply crossover to this individual?
			if (this.crossoverRate > Math.random()) {
				// Initialize offspring
				Individual offspring = new Individual(parent1.getChromosomeLength());

				// Find second parent
				Individual parent2 = selectIndividual(population);
				offspring = crossover2Point(parent1, parent2);

				if (parent1.getFitness() <= calcFitness(offspring, fogDevices, cloudletList)
						&& !doesPopupationIncludeIndividual(population, offspring)) {
					newPopulation.add(offspring);
				} else {
					newPopulation.add(parent1);
				}
			} else {
				newPopulation.add(population.getFittest(populationIndex));
			}
		}
		population.getPopulation().clear();
		population.setPopulation(newPopulation);

//              System.out.println("--------AFTER CROSSOVER--------");
//              population.printPopulation();
		return population;
	}

// crossover 2 points between 2 parents and create an offspring
	public Individual crossover2Point(Individual parent1, Individual parent2) {
		Individual offspring = new Individual(parent1.getChromosomeLength());
		int crossoverPoint1 = Service.rand(0, parent1.getChromosomeLength() - 1);
		int crossoverPoint2 = Service.rand(crossoverPoint1 + 1, crossoverPoint1 + parent1.getChromosomeLength());

		for (int geneIndex = 0; geneIndex < parent1.getChromosomeLength(); geneIndex++) {
			if (crossoverPoint2 >= parent1.getChromosomeLength()) {
				if (geneIndex >= crossoverPoint1 || geneIndex < (crossoverPoint2 - parent1.getChromosomeLength())) {
					offspring.setGene(geneIndex, parent2.getGene(geneIndex));
				} else {
					offspring.setGene(geneIndex, parent1.getGene(geneIndex));
				}
			} else {
				if (geneIndex >= crossoverPoint1 && geneIndex < crossoverPoint2) {
					offspring.setGene(geneIndex, parent2.getGene(geneIndex));
				} else {
					offspring.setGene(geneIndex, parent1.getGene(geneIndex));
				}
			}
		}
		return offspring;
	}

	// crossover 2 points between 2 parents and create an offspring
	public List<Individual> crossover2Point2(Individual parent1, Individual parent2) {
		List<Individual> listOffsprings = new ArrayList<Individual>();
		Individual offspring1 = new Individual(parent1.getChromosomeLength());
		Individual offspring2 = new Individual(parent1.getChromosomeLength());
		int crossoverPoint1 = Service.rand(0, parent1.getChromosomeLength() - 1);
		int crossoverPoint2 = Service.rand(crossoverPoint1 + 1, crossoverPoint1 + parent1.getChromosomeLength() - 1);

		for (int geneIndex = 0; geneIndex < parent1.getChromosomeLength(); geneIndex++) {
			if (crossoverPoint2 >= parent1.getChromosomeLength()) {
				if (geneIndex >= crossoverPoint1 || geneIndex < (crossoverPoint2 - parent1.getChromosomeLength())) {
					offspring1.setGene(geneIndex, parent2.getGene(geneIndex));
					offspring2.setGene(geneIndex, parent1.getGene(geneIndex));
				} else {
					offspring1.setGene(geneIndex, parent1.getGene(geneIndex));
					offspring2.setGene(geneIndex, parent2.getGene(geneIndex));
				}
			} else {
				if (geneIndex >= crossoverPoint1 && geneIndex < crossoverPoint2) {
					offspring1.setGene(geneIndex, parent2.getGene(geneIndex));
					offspring2.setGene(geneIndex, parent1.getGene(geneIndex));
				} else {
					offspring1.setGene(geneIndex, parent1.getGene(geneIndex));
					offspring2.setGene(geneIndex, parent2.getGene(geneIndex));
				}
			}
		}
		listOffsprings.add(offspring1);
		listOffsprings.add(offspring2);
		return listOffsprings;
	}

// crossover 1 points between 2 parents and create an offspring
	public Individual crossover1Point(Individual parent1, Individual parent2) {
		Individual offspring = new Individual(parent1.getChromosomeLength());
		int crossoverPoint = Service.rand(0, parent1.getChromosomeLength());
		for (int geneIndex = 0; geneIndex < parent1.getChromosomeLength(); geneIndex++) {
			// Use half of parent1's genes and half of parent2's genes
			if (crossoverPoint > geneIndex) {
				offspring.setGene(geneIndex, parent1.getGene(geneIndex));
			} else {
				offspring.setGene(geneIndex, parent2.getGene(geneIndex));
			}
		}
		return offspring;
	}

	/**
	 * Apply mutation to population
	 *
	 * Mutation affects individuals rather than the population. We look at each
	 * individual in the population, and if they're lucky enough (or unlucky, as it
	 * were), apply some randomness to their chromosome. Like crossover, the type of
	 * mutation applied depends on the specific problem we're solving. In this case,
	 * we simply randomly flip 0s to 1s and vice versa.
	 *
	 * This method will consider the GeneticAlgorithm instance's mutationRate and
	 * elitismCount
	 *
	 * @param population The population to apply mutation to
	 * @return The mutated population
	 */
	public Population mutatePopulation(Population population, List<FogDevice> fogDevices,
			List<? extends Cloudlet> cloudletList) {

		// Loop over current population by fitness
		for (int populationIndex = 0; populationIndex < population.size(); populationIndex++) {
			// if the current individual is selected to mutation phase
			if (this.mutationRate > Math.random() && populationIndex >= this.elitismCount) {
				Individual individual = population.getFittest(populationIndex);
				individual.setGene(Service.rand(0, individual.getChromosomeLength() - 1),
						Service.rand(0, individual.getMaxValue()));

//                              Individual newIndividual = new Individual(individual.getChromosomeLength());
//
//                              //listChange contains which gen change makes the individual better
//                              List<Pair> listChange = new ArrayList<Pair>();
//
//
//                              for(int cloudletId = 0; cloudletId < individual.getChromosomeLength(); cloudletId++) {
//                                      for(int fogId = 0; fogId < individual.getMaxValue() + 1; fogId++) {
//
//                                              for(int geneIndex = 0; geneIndex < newIndividual.getChromosomeLength(); geneIndex++) {
//                                                      newIndividual.setGene(geneIndex, individual.getGene(geneIndex));
//                                              }
//
//                                              // change a gene of individual to form newIndividual
//                                              newIndividual.setGene(cloudletId, fogId);
//                                              double newFitness = calcFitness(newIndividual, fogDevices, cloudletList);
//                                              //if newIndividual is better then individual, store change in listChange
//                                              if(newFitness > individual.getFitness()) {
//                                                      listChange.add(new Pair(cloudletId, fogId));
//                                              }
//                                      }
//                              }
//
//                              // if exist any gene make individual better, select randomly a gene change to have newIndividual
//                              if(!listChange.isEmpty()) {
//                                      int change = Service.rand(0, listChange.size() - 1);
//                                      individual.setGene(listChange.get(change).getCloudletId(), listChange.get(change).getFogId());
//                              }
			}
		}
		// Return mutated population
		return population;
	}

	public boolean doesPopupationIncludeIndividual(Population population, Individual individual) {
		boolean include = false;
		for (int index = 0; index < population.size(); index++) {
			boolean similar = true;
			if (individual.getFitness() == population.getIndividual(index).getFitness()) {
				for (int geneIndex = 0; geneIndex < individual.getChromosomeLength(); geneIndex++) {
					if (individual.getGene(geneIndex) != population.getIndividual(index).getGene(geneIndex)) {
						similar = false;
					}
				}
				if (similar == true) {
					include = true;
					break;
				}
			}
			if (include == true)
				break;
		}
		return include;
	}

	public void selectPopulation(Population population) {
		// remove similar individuals
//              population.sortPopulation();
////            System.out.println("--------before select--------");
////            population.printPopulation();
//              for(int populationIndex = 0; populationIndex < population.size()-1; populationIndex++) {
//                      for(int index = populationIndex +1; index < population.size(); index++) {
//                              if(population.getIndividual(populationIndex).getFitness() == population.getIndividual(index).getFitness()) {
//                                      boolean similar = true;
//                                      for(int geneIndex = 0; geneIndex < population.getIndividual(populationIndex).getChromosomeLength(); geneIndex++) {
//                                              if(population.getIndividual(populationIndex).getGene(geneIndex) != population.getIndividual(index).getGene(geneIndex)) {
//                                                      similar = false;
//                                              }
//                                      }
//                                      if(similar == true) {
//                                              population.getPopulation().remove(populationIndex);
//                                              populationIndex--;
//                                              break;
//                                      }
//                              }
//                      }
//              }

		population.sortPopulation();

		System.out.println("Before Selection: ");
		population.printPopulation();

		while (population.size() > SchedulingAlgorithm.NUMBER_INDIVIDUAL) {
			population.getPopulation().remove(SchedulingAlgorithm.NUMBER_INDIVIDUAL);
		}
		System.out.println("After Selection: ");
		population.printPopulation();

//              System.out.println("--------AFTER select--------");
//              population.printPopulation();

//              // Create new population
//              List<Individual> newPopulation = new ArrayList<Individual>();
//              for(int populationIndex = 0; populationIndex < SchedulingAlgorithm.NUMBER_INDIVIDUAL; populationIndex++) {
////                    if(populationIndex < SchedulingAlgorithm.NUMBER_ELITISM_INDIVIDUAL) {
////                            newPopulation.add(population.getIndividual(populationIndex));
////                    } else {
////                            Individual individual = selectIndividual(population);
////                            newPopulation.add(individual);
////                    }
//                      newPopulation.add(population.getFittest(populationIndex));
//              }
//              population.getPopulation().clear();
//              population.setPopulation(newPopulation);
	}

	public double getMinTime() {
		return this.minTime;
	}

	public double getMinCost() {
		return this.minCost;
	}

	public boolean isSameIndividual(Individual individual1, Individual individual2) {
		boolean same = true;
		for (int geneIndex = 0; geneIndex < individual1.getChromosomeLength(); geneIndex++) {
			if (individual1.getGene(geneIndex) != individual2.getGene(geneIndex)) {
				same = false;
				break;
			}
		}
		return same;
	}

	public static void main(String[] args) {

	}

	public Population crossoverPopulation2(Population population, List<FogDevice> fogDevices,
			List<? extends Cloudlet> cloudletList) {
		Population newPopulation = new Population();

		// Copy some individuals to population of next generation
		int numberOfParentPairs = (int) (population.size() * this.crossoverRate / 2);
		int numberOfCopyIndividuals = population.size() - 2 * numberOfParentPairs;

		for (int index = 0; index < numberOfCopyIndividuals; index++) {
			if (index < this.elitismCount) {
				newPopulation.getPopulation().add(population.getFittest(index));
			} else {
				Individual individual = this.selectIndividual(population);
				newPopulation.getPopulation().add(individual);
			}
		}

		// Loop over current population by fitness
		for (int loopIndex = 0; loopIndex < numberOfParentPairs; loopIndex++) {
			// Initialize offspring
			Individual parent1 = this.selectIndividual(population);
			Individual parent2 = new Individual(parent1.getChromosomeLength());
			do {
				// Find second parent
				parent2 = this.selectIndividual(population);
			} while (isSameIndividual(parent1, parent2));

			List<Individual> listOffsprings = crossover2Point2(parent1, parent2);

//                      parent1.printGene();
//                      System.out.println("");
//                      parent2.printGene();
//                      System.out.println("");
//                      listOffsprings.get(0).printGene();
//                      System.out.println("");
//                      listOffsprings.get(1).printGene();
//                      System.out.println("");

			newPopulation.getPopulation().add(listOffsprings.get(0));
			newPopulation.getPopulation().add(listOffsprings.get(1));
		}
		return newPopulation;
	}

	public Population mutatePopulation2(Population newPopulation, List<FogDevice> fogDevices,
			List<? extends Cloudlet> cloudletList) {
		// Loop over current population by fitness
		for (int populationIndex = 0; populationIndex < newPopulation.size(); populationIndex++) {
			// if the current individual is selected to mutation phase
			if (this.mutationRate > Math.random() && populationIndex >= this.elitismCount) {
				Individual individual = newPopulation.getFittest(populationIndex);
				individual.setGene(Service.rand(0, individual.getChromosomeLength() - 1),
						Service.rand(0, individual.getMaxValue()));
			}
		}
		return newPopulation;
	}

	public Population selectPopulation2(Population population, Population newPopulation, List<FogDevice> fogDevices,
			List<? extends Cloudlet> cloudletList) {

		for (Individual individual : newPopulation.getPopulation()) {
			population.getPopulation().add(individual);
		}
		newPopulation.getPopulation().clear();
		population = this.evalPopulation(population, fogDevices, cloudletList);

		System.out.println("Before Selection: ");
		population.printPopulation();

		while (population.size() > SchedulingAlgorithm.NUMBER_INDIVIDUAL) {
			population.getPopulation().remove(SchedulingAlgorithm.NUMBER_INDIVIDUAL);
		}
//              System.out.println("After Selection: ");
//              population.printPopulation();
		return population;
	}

}
