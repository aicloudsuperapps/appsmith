package com.appsmith.external.plugins;

import com.appsmith.external.dtos.ExecuteActionDTO;
import com.appsmith.external.helpers.MustacheHelper;
import com.appsmith.external.models.ActionConfiguration;
import com.appsmith.external.models.ActionExecutionResult;
import com.appsmith.external.models.DatasourceConfiguration;
import com.appsmith.external.models.DatasourceStructure;
import com.appsmith.external.models.DatasourceTestResult;
import com.appsmith.external.models.Param;
import com.appsmith.external.models.Property;
import com.appsmith.external.models.TriggerRequestDTO;
import com.appsmith.external.models.TriggerResultDTO;
import org.pf4j.ExtensionPoint;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.appsmith.external.helpers.PluginUtils.getHintMessageForLocalhostUrl;

public interface PluginExecutor<C> extends ExtensionPoint {

    /**
     * This function is implemented by the plugins by default to execute the action.
     * <p>
     * If executeParametrized has a custom implementation by a plugin, this function would not be used.
     *
     * @param connection              : This is the connection that is established to the data source. This connection is according
     *                                to the parameters in Datasource Configuration
     * @param datasourceConfiguration : These are the configurations which have been used to create a Datasource from a Plugin
     * @param actionConfiguration     : These are the configurations which have been used to create an Action from a Datasource.
     * @return ActionExecutionResult  : This object is returned to the user which contains the result values from the execution.
     */
    Mono<ActionExecutionResult> execute(C connection, DatasourceConfiguration datasourceConfiguration, ActionConfiguration actionConfiguration);

    /**
     * This function is responsible for creating the connection to the data source and returning the connection variable
     * on success. For executing actions, this connection object would be passed for each function call.
     *
     * @param datasourceConfiguration
     * @return Connection object
     */
    Mono<C> datasourceCreate(DatasourceConfiguration datasourceConfiguration);

    /**
     * This function is used to bring down/destroy the connection to the data source.
     *
     * @param connection
     */
    void datasourceDestroy(C connection);

    /**
     * This function tells the platform if datasource is valid by checking the set of invalid strings.
     * If empty, the datasource is valid. This set of invalid strings is populated by
     * {@link #validateDatasource(DatasourceConfiguration)}
     *
     * @param datasourceConfiguration
     * @return boolean
     */
    default boolean isDatasourceValid(DatasourceConfiguration datasourceConfiguration) {
        return CollectionUtils.isEmpty(validateDatasource(datasourceConfiguration));
    }

    /**
     * This function checks if the datasource is valid. It should only check if all the mandatory fields are filled and
     * if the values are of the right format. It does NOT check the validity of those fields.
     * Please use {@link #testDatasource(DatasourceConfiguration)} to establish the correctness of those fields.
     * <p>
     * If the datasource configuration is valid, it should return an empty set of invalid strings.
     * If not, it should return the list of invalid messages as a set.
     *
     * @param datasourceConfiguration : The datasource to be validated
     * @return Set                      : The set of invalid strings informing the user of all the invalid fields
     */
    Set<String> validateDatasource(DatasourceConfiguration datasourceConfiguration);

    /**
     * This function tests the datasource by executing a test query or hitting the endpoint to check the correctness
     * of the values provided in the datasource configuration
     *
     * @param datasourceConfiguration
     * @return
     */
    Mono<DatasourceTestResult> testDatasource(DatasourceConfiguration datasourceConfiguration);

    /**
     * This function fetches the structure of the tables/collections in the datasource. It's used to make query creation
     * easier for the user.
     *
     * @param connection
     * @param datasourceConfiguration
     * @return
     */
    default Mono<DatasourceStructure> getStructure(C connection, DatasourceConfiguration datasourceConfiguration) {
        return Mono.empty();
    }

    /**
     * This function executes the DB query to fetch details about the datasource when we don't want to create new action
     * just to get the information about the datasource
     * e.g. Get Spreadsheets from Google Drive, Get first row in datasource etc.
     *
     * @param pluginSpecifiedTemplates
     * @param datasourceConfiguration
     * @return
     */
    default Mono<ActionExecutionResult> getDatasourceMetadata(List<Property> pluginSpecifiedTemplates, DatasourceConfiguration datasourceConfiguration) {
        return Mono.empty();
    }

    /**
     * Appsmith Server calls this function for execution of the action.
     * Default implementation which takes the variables that need to be substituted and then calls the plugin execute function
     * <p>
     * Plugins requiring their custom implementation of variable substitution should override this function and then are
     * responsible both for variable substitution and final execution.
     *
     * @param connection              : This is the connection that is established to the data source. This connection is according
     *                                to the parameters in Datasource Configuration
     * @param executeActionDTO        : This is the data structure sent by the client during execute. This contains the params
     *                                which would be used for substitution
     * @param datasourceConfiguration : These are the configurations which have been used to create a Datasource from a Plugin
     * @param actionConfiguration     : These are the configurations which have been used to create an Action from a Datasource.
     * @return ActionExecutionResult  : This object is returned to the user which contains the result values from the execution.
     */
    default Mono<ActionExecutionResult> executeParameterized(C connection,
                                                             ExecuteActionDTO executeActionDTO,
                                                             DatasourceConfiguration datasourceConfiguration,
                                                             ActionConfiguration actionConfiguration) {
        prepareConfigurationsForExecution(executeActionDTO, actionConfiguration, datasourceConfiguration);
        return this.execute(connection, datasourceConfiguration, actionConfiguration);
    }

    /**
     * This function is responsible for preparing the action and datasource configurations to be ready for execution.
     *
     * @param executeActionDTO
     * @param actionConfiguration
     * @param datasourceConfiguration
     */
    default void prepareConfigurationsForExecution(ExecuteActionDTO executeActionDTO,
                                                   ActionConfiguration actionConfiguration,
                                                   DatasourceConfiguration datasourceConfiguration) {

        variableSubstitution(actionConfiguration, datasourceConfiguration, executeActionDTO);

    }

    /**
     * This function replaces the variables in the action and datasource configuration with the actual params
     */
    default void variableSubstitution(ActionConfiguration actionConfiguration,
                                      DatasourceConfiguration datasourceConfiguration,
                                      ExecuteActionDTO executeActionDTO) {
        //Do variable substitution
        //Do this only if params have been provided in the execute command
        if (executeActionDTO != null && !CollectionUtils.isEmpty(executeActionDTO.getParams())) {
            Map<String, String> replaceParamsMap = executeActionDTO
                    .getParams()
                    .stream()
                    .collect(Collectors.toMap(
                            // Trimming here for good measure. If the keys have space on either side,
                            // Mustache won't be able to find the key.
                            // We also add a backslash before every double-quote or backslash character
                            // because we apply the template replacing in a JSON-stringified version of
                            // these properties, where these two characters are escaped.
                            p -> p.getKey().trim(), // .replaceAll("[\"\n\\\\]", "\\\\$0"),
                            Param::getValue,
                            // In case of a conflict, we pick the older value
                            (oldValue, newValue) -> oldValue)
                    );

            MustacheHelper.renderFieldValues(datasourceConfiguration, replaceParamsMap);
            MustacheHelper.renderFieldValues(actionConfiguration, replaceParamsMap);
        }
    }

    /**
     * This method generates hint messages after reading the action configuration and the datasource configuration
     * defined by user. Each plugin must override this method to provide their plugin specific hint messages - since
     * the configuration related constraints can only be meaningfully interpreted by the respective plugins for which
     * they are defined. Otherwise, this default implementation will be used.
     *
     * It generates two set of hint messages - one for action configuration and another for the datasource
     * configuration. The datasource related hint messages are meant to be displayed on the datasource configuration
     * page and the action related hint messages are meant to be displayed on the query editor page.
     *
     * @param actionConfiguration
     * @param datasourceConfiguration
     * @return A tuple of datasource and action configuration related hint messages.
     */
    default Mono<Tuple2<Set<String>, Set<String>>> getHintMessages(ActionConfiguration actionConfiguration,
                                                                        DatasourceConfiguration datasourceConfiguration) {
        Set<String> datasourceHintMessages = new HashSet<>();
        Set<String> actionHintMessages = new HashSet<>();

        datasourceHintMessages.addAll(getHintMessageForLocalhostUrl(datasourceConfiguration));

        return Mono.zip(Mono.just(datasourceHintMessages), Mono.just(actionHintMessages));
    }

    default Mono<TriggerResultDTO> trigger(TriggerRequestDTO request) {
        return Mono.empty();
    }

    /**
     * This method coverts a plugin's form data to its native query. Currently, it is meant to help users
     * switch easily from form based input to raw input mode by providing a readily available translation of the form
     * data to raw query. It stores its result at the following two keys:
     *   o formToNativeQuery.status: success / error
     *   o formToNativeQuery.data: translated raw query if status is success or error message if status is error.
     * Each plugin must override this method to provide their own translation logic.
     * @param actionConfiguration
     * @return modified actionConfiguration object after setting the two keys mentioned above in `formData`.
     */
    default void extractAndSetNativeQueryFromFormData(ActionConfiguration actionConfiguration) {
    }
}
