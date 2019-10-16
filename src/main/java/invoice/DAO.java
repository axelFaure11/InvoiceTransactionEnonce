package invoice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.omg.CORBA.INITIALIZE;

public class DAO {

	private final DataSource myDataSource;

	/**
	 *
	 * @param dataSource la source de données à utiliser
	 */
	public DAO(DataSource dataSource) {
		this.myDataSource = dataSource;
	}

	/**
	 * Renvoie le chiffre d'affaire d'un client (somme du montant de ses factures)
	 *
	 * @param id la clé du client à chercher
	 * @return le chiffre d'affaire de ce client ou 0 si pas trouvé
	 * @throws SQLException
	 */
	public float totalForCustomer(int id) throws SQLException {
		String sql = "SELECT SUM(Total) AS Amount FROM Invoice WHERE CustomerID = ?";
		float result = 0;
		try (Connection connection = myDataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, id); // On fixe le 1° paramètre de la requête
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					result = resultSet.getFloat("Amount");
				}
			}
		}
		return result;
	}

	/**
	 * Renvoie le nom d'un client à partir de son ID
	 *
	 * @param id la clé du client à chercher
	 * @return le nom du client (LastName) ou null si pas trouvé
	 * @throws SQLException
	 */
	public String nameOfCustomer(int id) throws SQLException {
		String sql = "SELECT LastName FROM Customer WHERE ID = ?";
		String result = null;
		try (Connection connection = myDataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					result = resultSet.getString("LastName");
				}
			}
		}
		return result;
	}

	/**
	 * Transaction permettant de créer une facture pour un client
	 *
	 * @param customer Le client
	 * @param productIDs tableau des numéros de produits à créer dans la facture
	 * @param quantities tableau des quantités de produits à facturer faux sinon Les deux tableaux doivent avoir la même
	 * taille
	 * @throws java.lang.Exception si la transaction a échoué
	 */
	public void createInvoice(CustomerEntity customer, int[] productIDs, int[] quantities)
		throws Exception {
                
                String sql = "INSERT INTO Invoice VALUES (?, ?, ?)";
                String sql1 = "INSERT INTO Item VALUES (?, ?, ?, ?, ?)";
                String sql2 = "SELECT price FROM Product WHERE ID = ?";
                
                try(Connection connection = myDataSource.getConnection();
                    PreparedStatement pStmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement pStmt1 = connection.prepareStatement(sql1, Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement pStmt2 = connection.prepareStatement(sql2))
                {
                    //On désactive l'autocommit le temps de nos opérations
                    connection.setAutoCommit(false);
                    
                    //On essaye de récupérer une clé générée pour un nouvel invoice
                    int invoiceKey = 0;
                    try(ResultSet keys = pStmt.getGeneratedKeys())
                    { if(keys.next()) invoiceKey = keys.getInt(0);
                    
                        try {
                            /*
                            *On créer un invoice avec la clé générée,
                            *l'ID du client et un total de 0 (total est
                            *automatiquement mis à jour avec les triggers.
                            */
                            pStmt.setInt(1, invoiceKey);
                            pStmt.setInt(2, customer.getCustomerId());
                            pStmt.setInt(3, 0);
                        
                            //On essaye d'insérer le nouvel invoice
                            int invoiceUpdated = pStmt.executeUpdate();
                            if(invoiceUpdated == 0) throw new Exception();
                        
                            
                            //On essaye de récupérer une clé pour un nouvel Item
                            int itemKey = 0;
                            try(ResultSet keysItem = pStmt1.getGeneratedKeys()){
                                //Pour chaque produit, on créer un Item
                                for(int i = 0; i<productIDs.length; i++){
                                    if(keysItem.next()){
                                        itemKey = keysItem.getInt(0);
                                    }
                                    try {
                                        //On prépare le nouvel Item
                                        pStmt1.setInt(1, invoiceKey);
                                        pStmt1.setInt(2, itemKey);
                                        pStmt1.setInt(3, productIDs[i]);
                                        pStmt1.setInt(4, quantities[i]);
                                        
                                        //On récupère le prix du produit
                                        pStmt2.setInt(1, productIDs[i]);
                                        ResultSet rscost = pStmt2.executeQuery();
                                        int prix = 0;
                                        //Si on peut récupérer le prix on le stocke
                                        if(rscost.next()){
                                            prix = rscost.getInt("PRICE");
                                        //Sinon il y a un problème et on lève une exception
                                        } else {
                                            throw new Exception();
                                        }
                                        pStmt1.setInt(5, prix);
                                    } catch (Exception e){
                                        throw e;
                                    }
                                }
                            }
                            //On insère le nouvel Item
                            int numberUpdated = pStmt1.executeUpdate();
                            if(numberUpdated == 0) throw new Exception();
                            
                            /*
                            *Si on arrive ici c'est que tout a fonctionné
                            *donc on commit
                            */
                            connection.commit();
                        } catch (Exception e){
                            throw e;
                        }
                    } catch (Exception e){
                        connection.rollback();
                    } finally {
                        connection.setAutoCommit(true);
                    }
                }
	}

	/**
	 *
	 * @return le nombre d'enregistrements dans la table CUSTOMER
	 * @throws SQLException
	 */
	public int numberOfCustomers() throws SQLException {
		int result = 0;

		String sql = "SELECT COUNT(*) AS NUMBER FROM Customer";
		try (Connection connection = myDataSource.getConnection();
			Statement stmt = connection.createStatement()) {
			ResultSet rs = stmt.executeQuery(sql);
			if (rs.next()) {
				result = rs.getInt("NUMBER");
			}
		}
		return result;
	}

	/**
	 *
	 * @param customerId la clé du client à recherche
	 * @return le nombre de bons de commande pour ce client (table PURCHASE_ORDER)
	 * @throws SQLException
	 */
	public int numberOfInvoicesForCustomer(int customerId) throws SQLException {
		int result = 0;

		String sql = "SELECT COUNT(*) AS NUMBER FROM Invoice WHERE CustomerID = ?";

		try (Connection connection = myDataSource.getConnection();
			PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, customerId);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				result = rs.getInt("NUMBER");
			}
		}
		return result;
	}

	/**
	 * Trouver un Customer à partir de sa clé
	 *
	 * @param customedID la clé du CUSTOMER à rechercher
	 * @return l'enregistrement correspondant dans la table CUSTOMER, ou null si pas trouvé
	 * @throws SQLException
	 */
	CustomerEntity findCustomer(int customerID) throws SQLException {
		CustomerEntity result = null;

		String sql = "SELECT * FROM Customer WHERE ID = ?";
		try (Connection connection = myDataSource.getConnection();
			PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, customerID);

			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				String name = rs.getString("FirstName");
				String address = rs.getString("Street");
				result = new CustomerEntity(customerID, name, address);
			}
		}
		return result;
	}

	/**
	 * Liste des clients localisés dans un état des USA
	 *
	 * @param state l'état à rechercher (2 caractères)
	 * @return la liste des clients habitant dans cet état
	 * @throws SQLException
	 */
	List<CustomerEntity> customersInCity(String city) throws SQLException {
		List<CustomerEntity> result = new LinkedList<>();

		String sql = "SELECT * FROM Customer WHERE City = ?";
		try (Connection connection = myDataSource.getConnection();
			PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setString(1, city);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					int id = rs.getInt("ID");
					String name = rs.getString("FirstName");
					String address = rs.getString("Street");
					CustomerEntity c = new CustomerEntity(id, name, address);
					result.add(c);
				}
			}
		}

		return result;
	}
}
