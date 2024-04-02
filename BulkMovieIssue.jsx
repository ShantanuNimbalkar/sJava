import React, { useState } from 'react';
import { Modal, ModalHeader, ModalBody } from 'reactstrap';
import { Icon, Message as SymMessage, Label } from 'semantic-ui-react';
import API from '../../utils/serverApi';
import CustomButton from '../../common/CustomButton';
import { CircularSpinner } from '../../common/Spinners';
import SearchableDropdown from '../../common/SearchableDropdown';
import * as HTTPRequestHandler from '../../utils/HttpRequestHandler';

const BulkMoveIssue = ({ openStatusModal, setOpenStatusModal, projectsList, handleConfirmProjectChange, userId, selectedTickets }) => {
    // blocked access for Property UW, Engineering UW, Coinsurance
    const ACCESS_RESTRICTED_PROJECTS = [99, 74, 86];
    const [ticketsMovedTo, setTicketsMovedTo] = useState('');
    const [selectedProjectName, setSelectedProjectName] = useState('');
    const [isTicketMoved, setTicketMoved] = useState(false);
    const [isSpinnerLoading, setSpinnerLoading] = useState(false);
    const [failedMovingErrorMessage, setFailedMovingErrorMessage] = useState('');
    const toggle = () => setOpenStatusModal(!openStatusModal);
    const closeBtn = <button size="md" style={{ color: 'white', height: '25px', width: '25px' }} className="bg-dark" onClick={toggle}>X</button>;
    const arrayFormatter = array => {
        let processedObject = {
            key: '',
            text: '',
            value: ''
        }
        let processedArray = [];
        array.filter(project => !ACCESS_RESTRICTED_PROJECTS.includes(project.id))
            .forEach(valueObj => {
                processedObject = {
                    key: valueObj.id,
                    text: valueObj.name,
                    value: valueObj.id
                }
                processedArray.push(processedObject);
            })
        return processedArray;
    }
    const handleProjectChange = (event, { value }) => {
        setTicketsMovedTo(value);
        setSelectedProjectName(event.target.textContent)
    }
    const handleSubmit = () => {
        setFailedMovingErrorMessage('');
        setSpinnerLoading(true);
        setTicketMoved(false);
        const data = {
            "targetProjectId": ticketsMovedTo,
            "issueId": selectedTickets.toString(),
            "userId": userId,
        }
        HTTPRequestHandler.PostRequest(API.MOVE_BULK_ISSUE_TO_ANATHER_PROJECT, data)
            .then(response => {
                setTicketMoved(true);
                setSpinnerLoading(false);
            })
            .catch(error => {
                setTicketMoved(false);
                setSpinnerLoading(false);
                setFailedMovingErrorMessage(`Oops! Something went wrong while processing...`);
            })
    }

    const handleOperationClose = () => {
        setOpenStatusModal(false);
        handleConfirmProjectChange(isTicketMoved, selectedProjectName);
    }

    return (
        <div>
            <Modal centered isOpen={openStatusModal} toggle={toggle} className="modal-container">
                <ModalHeader style={{ width: '100%' }} close={!isTicketMoved ? closeBtn : null} className="bg-dark">
                    <p style={{ color: 'white' }}>Move Issues</p>
                </ModalHeader>
                <ModalBody>
                    <SymMessage warning style={{ textAlign: 'center' }}>
                        <Icon name='warning' />
                        Please Wait while fetching the data. <br /> To move tickets, search & select project from dropdown and Click confirm.
                    </SymMessage>
                    <SearchableDropdown
                        id='searchable-dropdown-bulk-set-assignee'
                        placeholder='Select Project'
                        // label='Change Assignee Name'
                        upward={false}
                        clearable={true}
                        array={arrayFormatter(projectsList)}
                        search={true}
                        selectOnBlur={false}
                        onChange={handleProjectChange}
                    />
                    {
                        isSpinnerLoading ? (
                            <div className="d-flex mt-3" style={{ height: "2rem" }}>
                                <div>
                                    <CircularSpinner />
                                </div>
                                <div>
                                    <p className="h6 ml-2 mt-1 text-warning">Updating Please Wait....</p>
                                </div>
                            </div>
                        ) : null
                    }
                    {
                        failedMovingErrorMessage !== '' ?
                            <div className='mt-3 ' style={{ display: 'flex', justifyContent: 'center' }}>
                                <Label size='large' color='red' >
                                    <Icon name='frown outline' size='large' />
                                    {failedMovingErrorMessage}
                                </Label>
                            </div> : null
                    }
                    <div className='mt-3'>
                        {
                            !isSpinnerLoading && !isTicketMoved ?
                                <CustomButton buttonText='Cancel' floated='left' color='yellow' style={{ letterSpacing: '1px', fontSize: '15px' }} onClick={toggle} />
                                : null
                        }
                        {
                            !isTicketMoved ?
                                <CustomButton disabled={isSpinnerLoading} buttonText='Confirm' floated='right' color='yellow' style={{ letterSpacing: '1px', fontSize: '15px' }} onClick={handleSubmit} />
                                : <div style={{ display: 'block', justifyContent: 'space-between' }}>
                                    <Label size='large' color='green' >
                                        Tickets moved successfully to <strong>{selectedProjectName}</strong>
                                    </Label>
                                    <CustomButton buttonText='Close' floated='right' color='yellow' style={{ letterSpacing: '1px', fontSize: '15px' }} onClick={handleOperationClose} />
                                </div>
                        }
                    </div>
                </ModalBody>
            </Modal>
        </div >
    )
}
export default BulkMoveIssue;